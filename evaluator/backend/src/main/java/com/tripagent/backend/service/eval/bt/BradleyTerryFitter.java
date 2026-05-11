package com.tripagent.backend.service.eval.bt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bradley-Terry MLE 拟合（梯度上升）。
 * 给定模型间的两两胜负记录，估计每个模型的潜在强度 θ。
 * 锚定第一个模型 θ_0 = 0 以保证解唯一。
 */
public class BradleyTerryFitter {

  private final int maxIter;
  private final double learningRate;
  private final double convergenceTol;

  public BradleyTerryFitter(int maxIter, double learningRate) {
    this(maxIter, learningRate, 1e-5);
  }

  public BradleyTerryFitter(int maxIter, double learningRate, double convergenceTol) {
    this.maxIter = maxIter;
    this.learningRate = learningRate;
    this.convergenceTol = convergenceTol;
  }

  /**
   * @param modelIds 模型 ID 列表（顺序决定 θ 向量索引；θ[0] 锚定 0）
   * @param wins 二维矩阵 wins[i][j] = i 战胜 j 的总场次（含 TIE 各 0.5）
   * @return Map: modelId → theta
   */
  public Map<Long, Double> fit(List<Long> modelIds, double[][] wins) {
    int n = modelIds.size();
    if (n < 2) {
      throw new IllegalArgumentException("BT 拟合至少需要 2 个模型");
    }
    if (wins.length != n || wins[0].length != n) {
      throw new IllegalArgumentException("wins 矩阵维度不匹配");
    }

    double[] theta = new double[n];  // 全 0 初始化
    double prevLogLik = Double.NEGATIVE_INFINITY;

    for (int iter = 0; iter < maxIter; iter++) {
      double[] grad = new double[n];
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          if (i == j) continue;
          double n_ij = wins[i][j] + wins[j][i];
          if (n_ij <= 0) continue;
          double p_ij = sigmoid(theta[i] - theta[j]);
          // ∂L/∂θ_i 来自 (i,j): wins[i][j] - n_ij * p_ij
          // 其中 wins[i][j] 是 i 胜 j 场次，n_ij * p_ij 是期望胜场
          grad[i] += wins[i][j] - n_ij * p_ij;
        }
      }
      // 锚定第一个模型: θ_0 = 0，对应 grad[0] = 0
      grad[0] = 0;

      // 梯度上升 (最大化 log-likelihood)
      for (int i = 0; i < n; i++) {
        theta[i] += learningRate * grad[i];
      }
      theta[0] = 0;  // 强制锚定

      double gradNorm = 0;
      for (int i = 0; i < n; i++) gradNorm += grad[i] * grad[i];
      gradNorm = Math.sqrt(gradNorm);
      if (gradNorm < convergenceTol) {
        break;
      }

      double logLik = computeLogLik(theta, wins, n);
      if (Math.abs(logLik - prevLogLik) < convergenceTol) {
        break;
      }
      prevLogLik = logLik;
    }

    Map<Long, Double> result = new LinkedHashMap<>();
    for (int i = 0; i < n; i++) {
      result.put(modelIds.get(i), theta[i]);
    }
    return result;
  }

  private double computeLogLik(double[] theta, double[][] wins, int n) {
    double sum = 0;
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (i == j) continue;
        if (wins[i][j] <= 0) continue;
        double diff = theta[i] - theta[j];
        // log P(i beats j) = diff - log(1 + exp(diff))
        sum += wins[i][j] * (diff - log1pExp(diff));
      }
    }
    return sum;
  }

  private static double sigmoid(double x) {
    if (x >= 0) {
      double z = Math.exp(-x);
      return 1.0 / (1.0 + z);
    } else {
      double z = Math.exp(x);
      return z / (1.0 + z);
    }
  }

  /** 数值稳定的 log(1 + exp(x)) */
  private static double log1pExp(double x) {
    if (x > 0) {
      return x + Math.log1p(Math.exp(-x));
    } else {
      return Math.log1p(Math.exp(x));
    }
  }

  /** 工具：将 EvalComparison 列表聚合为 wins 矩阵。canonical 视角：modelA 胜 → wins[i_A][i_B]++ */
  public static AggregatedPairs aggregate(
      List<Long> modelIds,
      List<Long> aSide,
      List<Long> bSide,
      List<Integer> resultCodes
  ) {
    Map<Long, Integer> idx = new LinkedHashMap<>();
    for (int i = 0; i < modelIds.size(); i++) {
      idx.put(modelIds.get(i), i);
    }
    int n = modelIds.size();
    double[][] wins = new double[n][n];

    for (int k = 0; k < aSide.size(); k++) {
      Integer i = idx.get(aSide.get(k));
      Integer j = idx.get(bSide.get(k));
      if (i == null || j == null || i.equals(j)) continue;
      int code = resultCodes.get(k);
      switch (code) {
        case 1 -> wins[i][j] += 1;          // A_PREFERRED
        case 2 -> wins[j][i] += 1;          // B_PREFERRED
        case 3 -> {                         // TIE → 0.5/0.5
          wins[i][j] += 0.5;
          wins[j][i] += 0.5;
        }
        default -> { /* INVALID skipped */ }
      }
    }
    return new AggregatedPairs(modelIds, wins);
  }

  public record AggregatedPairs(List<Long> modelIds, double[][] wins) {
    public int size() { return modelIds.size(); }

    /** 该模型参与的总比较场次 (含 TIE 的 0.5/0.5) */
    public double totalForModel(int idx) {
      int n = modelIds.size();
      double sum = 0;
      for (int j = 0; j < n; j++) {
        sum += wins[idx][j] + wins[j][idx];
      }
      return sum;
    }

    /** 该模型胜场（含 TIE 的 0.5） */
    public double winsForModel(int idx) {
      int n = modelIds.size();
      double sum = 0;
      for (int j = 0; j < n; j++) sum += wins[idx][j];
      return sum;
    }

    public List<Long> ids() { return new ArrayList<>(modelIds); }
  }
}
