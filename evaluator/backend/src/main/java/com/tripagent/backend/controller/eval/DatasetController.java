package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.DatasetResponse;
import com.tripagent.backend.dto.eval.DatasetSampleResponse;
import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.entity.enums.DatasetSource;
import com.tripagent.backend.service.eval.DatasetService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/eval/datasets")
public class DatasetController {

  private final DatasetService datasetService;

  public DatasetController(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @GetMapping
  public ResponseEntity<EvalApiResponse<List<DatasetResponse>>> list(
      @RequestParam(required = false) DatasetSource source,
      @RequestParam(required = false) Boolean enabledOnly
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(datasetService.list(source, enabledOnly)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<EvalApiResponse<DatasetResponse>> get(@PathVariable Long id) {
    return ResponseEntity.ok(EvalApiResponse.success(datasetService.get(id)));
  }

  @GetMapping("/{id}/samples")
  public ResponseEntity<EvalApiResponse<List<DatasetSampleResponse>>> getSamples(
      @PathVariable Long id,
      @RequestParam(required = false, defaultValue = "10") Integer limit
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(datasetService.getSamples(id, limit == null ? 10 : limit)));
  }

  @PostMapping(consumes = "multipart/form-data")
  public ResponseEntity<EvalApiResponse<DatasetResponse>> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam("name") String name,
      @RequestParam(value = "displayName", required = false) String displayName,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "owner", required = false) String owner
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(
        datasetService.upload(file, name, displayName, description, owner)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<EvalApiResponse<Boolean>> delete(@PathVariable Long id) {
    datasetService.softDelete(id);
    return ResponseEntity.ok(EvalApiResponse.success(Boolean.TRUE));
  }
}
