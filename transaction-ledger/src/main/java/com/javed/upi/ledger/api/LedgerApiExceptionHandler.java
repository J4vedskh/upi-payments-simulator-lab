package com.javed.upi.ledger.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class LedgerApiExceptionHandler {

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiErrorResponse.of(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
        "UNSUPPORTED_MEDIA_TYPE",
        "Content-Type is not supported for this endpoint.",
        request.getRequestURI(),
        Map.of()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ApiErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        "VALIDATION_FAILED",
        "Request body is malformed or missing a required field.",
        request.getRequestURI(),
        Map.of()));
  }

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

