package com.tripagent.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "bt")
public class BtProperties {

  private Integer bootstrapRounds = 200;
  private Integer eloAnchor = 1000;
  private Integer eloScale = 400;
  private Integer fitMaxIter = 200;
  private Double fitLr = 0.1;
}
