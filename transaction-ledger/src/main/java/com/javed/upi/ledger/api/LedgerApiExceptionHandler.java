package com.javed.upi.ledger.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class LedgerApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiErrorResponse> handleResponseStatus(
      ResponseStatusException exception,
      HttpServletRequest request) {
    int status = exception.getStatusCode().value();
    String reason = exception.getReason() == null ? exception.getMessage() : exception.getReason();

    return ResponseEntity.status(exception.getStatusCode()).body(ApiErrorResponse.of(
        status,
        exception.getStatusCode().toString(),
        reason,
        request.getRequestURI(),
        Map.of()));
  }
}

