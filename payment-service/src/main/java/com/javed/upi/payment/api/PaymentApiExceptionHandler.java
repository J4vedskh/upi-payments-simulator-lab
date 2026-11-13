package com.javed.upi.payment.api;

import com.javed.upi.payment.service.PaymentEventPublicationException;
import com.javed.upi.payment.service.PaymentIdempotencyException;
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

  @ExceptionHandler(PaymentEventPublicationException.class)
  ResponseEntity<ApiErrorResponse> handlePublicationFailure(
      PaymentEventPublicationException exception,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiErrorResponse.of(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        "EVENT_PUBLICATION_FAILED",
        exception.getMessage(),
        request.getRequestURI(),
        Map.of()));
  }

  @ExceptionHandler(PaymentIdempotencyException.class)
  ResponseEntity<ApiErrorResponse> handleIdempotency(
      PaymentIdempotencyException exception,
      HttpServletRequest request) {
    boolean conflict = exception.reason() == PaymentIdempotencyException.Reason.CONFLICT;
    HttpStatus status = conflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
    String error = conflict ? "IDEMPOTENCY_CONFLICT" : "VALIDATION_FAILED";
    return ResponseEntity.status(status).body(ApiErrorResponse.of(
        status.value(),
        error,
        exception.getMessage(),
        request.getRequestURI(),
        Map.of()));
  }

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
        errorCode(status),
        reason,
        request.getRequestURI(),
        Map.of()));
  }

  private static String errorCode(int status) {
    HttpStatus knownStatus = HttpStatus.resolve(status);
    return knownStatus == null ? Integer.toString(status) : knownStatus.name();
  }
}

