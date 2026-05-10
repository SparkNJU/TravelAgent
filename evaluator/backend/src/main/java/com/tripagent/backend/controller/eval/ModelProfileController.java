package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.CreateModelProfileRequest;
import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.dto.eval.ModelProfileResponse;
import com.tripagent.backend.dto.eval.UpdateModelProfileRequest;
import com.tripagent.backend.entity.enums.ModelRole;
import com.tripagent.backend.service.eval.ModelProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eval/models")
public class ModelProfileController {

  private final ModelProfileService service;

  public ModelProfileController(ModelProfileService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<EvalApiResponse<ModelProfileResponse>> create(
      @Valid @RequestBody CreateModelProfileRequest request
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(service.create(request)));
  }

  @GetMapping
  public ResponseEntity<EvalApiResponse<List<ModelProfileResponse>>> list(
      @RequestParam(required = false) ModelRole role,
      @RequestParam(required = false) Boolean enabledOnly
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(service.list(role, enabledOnly)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<EvalApiResponse<ModelProfileResponse>> get(@PathVariable Long id) {
    return ResponseEntity.ok(EvalApiResponse.success(service.get(id)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<EvalApiResponse<ModelProfileResponse>> update(
      @PathVariable Long id,
      @RequestBody UpdateModelProfileRequest request
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(service.update(id, request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<EvalApiResponse<Boolean>> delete(@PathVariable Long id) {
    service.softDelete(id);
    return ResponseEntity.ok(EvalApiResponse.success(Boolean.TRUE));
  }

  /** 硬删除：彻底从 DB 移除；若被历史 run 引用则拒绝。 */
  @DeleteMapping("/{id}/hard")
  public ResponseEntity<EvalApiResponse<Boolean>> hardDelete(@PathVariable Long id) {
    service.hardDelete(id);
    return ResponseEntity.ok(EvalApiResponse.success(Boolean.TRUE));
  }
}
