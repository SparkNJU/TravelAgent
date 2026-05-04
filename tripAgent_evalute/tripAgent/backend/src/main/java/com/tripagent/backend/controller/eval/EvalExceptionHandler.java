package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.EvalApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.tripagent.backend.controller.eval")
public class EvalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EvalApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(EvalApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
        .orElse("请求参数校验失败");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EvalApiResponse.error(message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<EvalApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EvalApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<EvalApiResponse<Object>> handleGeneric(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(EvalApiResponse.error("服务异常: " + ex.getMessage()));
  }
}
