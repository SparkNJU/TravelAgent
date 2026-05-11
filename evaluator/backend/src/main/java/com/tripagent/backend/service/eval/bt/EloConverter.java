package com.tripagent.backend.service.eval.bt;

/** θ → Elo 线性变换。Chatbot Arena 标准换算：elo = anchor + scale * theta / ln(10)。 */
public class EloConverter {

  private static final double LN10 = Math.log(10.0);

  private final double anchor;
  private final double scale;

  public EloConverter(double anchor, double scale) {
    this.anchor = anchor;
    this.scale = scale;
  }

  public double toElo(double theta) {
    return anchor + scale * theta / LN10;
  }
}
