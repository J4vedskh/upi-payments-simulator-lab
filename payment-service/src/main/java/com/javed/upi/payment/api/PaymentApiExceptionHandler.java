package com.javed.upi.payment.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class PaymentApiExceptionHandler {

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ApiErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        "VALIDATION_FAILED",
        "Request body is malformed.",
        request.getRequestURI(),
        Map.of()));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiErrorResponse.of(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
        "UNSUPPORTED_MEDIA_TYPE",
        "Content-Type is not supported for this endpoint.",
        request.getRequestURI(),
        Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException exception,
      HttpServletRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    }

    return ResponseEntity.badRequest().body(ApiErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        "VALIDATION_FAILED",
        "Request validation failed.",
        request.getRequestURI(),
        errors));
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

