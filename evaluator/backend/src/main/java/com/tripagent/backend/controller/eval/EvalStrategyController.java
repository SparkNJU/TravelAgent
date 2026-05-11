package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.CreateCustomMetricRequest;
import com.tripagent.backend.dto.eval.CreateEvalStrategyRequest;
import com.tripagent.backend.dto.eval.CustomMetricResponse;
import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.dto.eval.EvalStrategyResponse;
import com.tripagent.backend.dto.eval.UpdateEvalStrategyRequest;
import com.tripagent.backend.service.eval.CustomMetricService;
import com.tripagent.backend.service.eval.EvalStrategyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eval")
public class EvalStrategyController {

  private final EvalStrategyService evalStrategyService;
  private final CustomMetricService customMetricService;

  public EvalStrategyController(
      EvalStrategyService evalStrategyService,
      CustomMetricService customMetricService
  ) {
    this.evalStrategyService = evalStrategyService;
    this.customMetricService = customMetricService;
  }

  @PostMapping("/strategies")
  public ResponseEntity<EvalApiResponse<EvalStrategyResponse>> createStrategy(
      @Valid @RequestBody CreateEvalStrategyRequest request
  ) {
    EvalStrategyResponse response = evalStrategyService.createStrategy(request);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @GetMapping("/strategies")
  public ResponseEntity<EvalApiResponse<List<EvalStrategyResponse>>> listStrategies() {
    return ResponseEntity.ok(EvalApiResponse.success(evalStrategyService.listStrategies()));
  }

  @GetMapping("/strategies/{strategyId}")
  public ResponseEntity<EvalApiResponse<EvalStrategyResponse>> getStrategy(@PathVariable Long strategyId) {
    return ResponseEntity.ok(EvalApiResponse.success(evalStrategyService.getStrategy(strategyId)));
  }

  @PutMapping("/strategies/{strategyId}")
  public ResponseEntity<EvalApiResponse<EvalStrategyResponse>> updateStrategy(
      @PathVariable Long strategyId,
      @RequestBody UpdateEvalStrategyRequest request
  ) {
    EvalStrategyResponse response = evalStrategyService.updateStrategy(strategyId, request);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @PostMapping("/metrics/custom")
  public ResponseEntity<EvalApiResponse<CustomMetricResponse>> createCustomMetric(
      @Valid @RequestBody CreateCustomMetricRequest request
  ) {
    CustomMetricResponse response = customMetricService.createMetric(request);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @GetMapping("/metrics/custom")
  public ResponseEntity<EvalApiResponse<List<CustomMetricResponse>>> listCustomMetrics(
      @RequestParam(required = false) Boolean enabledOnly
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(customMetricService.listMetrics(enabledOnly)));
  }
}
