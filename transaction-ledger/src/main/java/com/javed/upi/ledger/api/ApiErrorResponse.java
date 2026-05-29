package com.javed.upi.ledger.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors) {

  public static ApiErrorResponse of(
      int status,
      String error,
      String message,
      String path,
      Map<String, String> validationErrors) {
    return new ApiErrorResponse(Instant.now(), status, error, message, path, validationErrors);
  }
}

