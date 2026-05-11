package com.tripagent.backend.service.eval.bt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 对 BT 拟合做 bootstrap 采样，产生每个模型 Elo 的 95% 置信区间。
 * 输入：原始比较记录（已 canonical 化为 modelA 胜负的整数编码）
 * 输出：modelId → [lowerCI95, upperCI95]
 */
public class BootstrapCi {

  private final BradleyTerryFitter fitter;
  private final EloConverter eloConverter;
  private final int rounds;
  private final Random random;

  public BootstrapCi(BradleyTerryFitter fitter, EloConverter eloConverter, int rounds) {
    this(fitter, eloConverter, rounds, new Random(42));
  }

  public BootstrapCi(BradleyTerryFitter fitter, EloConverter eloConverter, int rounds, Random random) {
    this.fitter = fitter;
    this.eloConverter = eloConverter;
    this.rounds = rounds;
    this.random = random;
  }

  /**
   * @param modelIds 全部模型 ID（决定输出顺序与 θ 锚定基准）
   * @param aSide 长度为 N 的列表：每条比较的 model_a_id
   * @param bSide 同上 model_b_id
   * @param resultCodes 同上 result 编码（1=A_PREFERRED, 2=B_PREFERRED, 3=TIE，其它跳过）
   * @return modelId → [lower, upper] Elo
   */
  public Map<Long, double[]> compute(
      List<Long> modelIds,
      List<Long> aSide,
      List<Long> bSide,
      List<Integer> resultCodes
  ) {
    int n = aSide.size();
    Map<Long, List<Double>> samples = new LinkedHashMap<>();
    for (Long id : modelIds) {
      samples.put(id, new ArrayList<>());
    }
    if (n == 0) {
      return emptyResult(modelIds);
    }

    int success = 0;
    for (int r = 0; r < rounds; r++) {
      List<Long> aBoot = new ArrayList<>(n);
      List<Long> bBoot = new ArrayList<>(n);
      List<Integer> rBoot = new ArrayList<>(n);
      for (int k = 0; k < n; k++) {
        int idx = random.nextInt(n);
        aBoot.add(aSide.get(idx));
        bBoot.add(bSide.get(idx));
        rBoot.add(resultCodes.get(idx));
      }
      try {
        BradleyTerryFitter.AggregatedPairs agg = BradleyTerryFitter.aggregate(modelIds, aBoot, bBoot, rBoot);
        Map<Long, Double> theta = fitter.fit(agg.modelIds(), agg.wins());
        for (Map.Entry<Long, Double> e : theta.entrySet()) {
          samples.get(e.getKey()).add(eloConverter.toElo(e.getValue()));
        }
        success++;
      } catch (Exception ignored) {
        // 该轮采样可能产生不连通图等问题，跳过
      }
    }

    Map<Long, double[]> result = new LinkedHashMap<>();
    for (Long id : modelIds) {
      List<Double> vals = samples.get(id);
      if (vals.isEmpty()) {
        result.put(id, new double[]{Double.NaN, Double.NaN});
      } else {
        double[] sorted = vals.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double lower = quantile(sorted, 0.025);
        double upper = quantile(sorted, 0.975);
        result.put(id, new double[]{lower, upper});
      }
    }
    return result;
  }

  private Map<Long, double[]> emptyResult(List<Long> modelIds) {
    Map<Long, double[]> empty = new HashMap<>();
    for (Long id : modelIds) {
      empty.put(id, new double[]{Double.NaN, Double.NaN});
    }
    return empty;
  }

  private double quantile(double[] sorted, double q) {
    if (sorted.length == 0) return Double.NaN;
    if (sorted.length == 1) return sorted[0];
    double pos = q * (sorted.length - 1);
    int low = (int) Math.floor(pos);
    int high = (int) Math.ceil(pos);
    if (low == high) return sorted[low];
    double frac = pos - low;
    return sorted[low] * (1 - frac) + sorted[high] * frac;
  }
}
