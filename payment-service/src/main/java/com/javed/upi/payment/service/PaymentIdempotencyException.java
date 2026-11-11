package com.javed.upi.payment.service;

public final class PaymentIdempotencyException extends RuntimeException {
  public enum Reason {
    INVALID_KEY,
    CONFLICT
  }

  private final Reason reason;

  private PaymentIdempotencyException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public static PaymentIdempotencyException invalidKey() {
    return new PaymentIdempotencyException(
        Reason.INVALID_KEY,
        "Idempotency-Key must contain 1 to 128 visible ASCII characters.");
  }

  public static PaymentIdempotencyException conflict() {
    return new PaymentIdempotencyException(
        Reason.CONFLICT,
        "Idempotency-Key was already used for a different payment request.");
  }

  public Reason reason() {
    return reason;
  }
}
