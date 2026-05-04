package com.tripagent.backend.dto.eval;

public record EvalApiResponse<T>(int code, String message, T data) {

  public static <T> EvalApiResponse<T> success(T data) {
    return new EvalApiResponse<>(200, "ok", data);
  }

  public static <T> EvalApiResponse<T> error(String message) {
    return new EvalApiResponse<>(400, message, null);
  }
}
