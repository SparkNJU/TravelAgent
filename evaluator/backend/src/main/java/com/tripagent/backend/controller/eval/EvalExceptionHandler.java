package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.EvalApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice(basePackages = "com.tripagent.backend.controller.eval")
public class EvalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(EvalExceptionHandler.class);

  /**
   * SSE / async responses use {@code text/event-stream}; returning {@link EvalApiResponse} would
   * trigger {@code HttpMessageNotWritableException} (no JSON converter for that content type).
   * Before the first byte, {@code getContentType()} may still be null — also match the stream URL.
   */
  private static boolean cannotReturnJsonBody(HttpServletResponse response) {
    if (response == null) {
      return false;
    }
    if (response.isCommitted()) {
      return true;
    }
    String ct = response.getContentType();
    return ct != null && ct.toLowerCase().contains("text/event-stream");
  }

  private static boolean isEvalRunStreamRequest(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String uri = request.getRequestURI();
    if (uri == null) {
      return false;
    }
    return uri.contains("/eval/runs/") && uri.endsWith("/stream");
  }

  private static boolean skipJsonErrorEnvelope(HttpServletRequest request, HttpServletResponse response) {
    return cannotReturnJsonBody(response) || isEvalRunStreamRequest(request);
  }

  /** Client disconnected or servlet output already broken — do not write a JSON error body. */
  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
    log.debug("Async request not usable (often SSE client disconnect): {}", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request, HttpServletResponse response) {
    if (skipJsonErrorEnvelope(request, response)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EvalApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request, HttpServletResponse response) {
    if (skipJsonErrorEnvelope(request, response)) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    return ResponseEntity.status(HttpStatus.CONFLICT).body(EvalApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request, HttpServletResponse response) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
        .orElse("请求参数校验失败");
    if (skipJsonErrorEnvelope(request, response)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EvalApiResponse.error(message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request, HttpServletResponse response) {
    if (skipJsonErrorEnvelope(request, response)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EvalApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<EvalApiResponse<Object>> handleGeneric(
      Exception ex, HttpServletRequest request, HttpServletResponse response) {
    if (skipJsonErrorEnvelope(request, response)) {
      log.debug("Skipping JSON error envelope for SSE/async response: {}", ex.toString());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(EvalApiResponse.error("服务异常: " + ex.getMessage()));
  }
}
