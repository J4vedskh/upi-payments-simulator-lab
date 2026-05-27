package com.javed.upi.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PaymentEvent(
    String paymentId,
    String payerVpa,
    String payeeVpa,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String decisionCode,
    String decisionMessage,
    Instant decidedAt) {

  public PaymentEvent {
    Objects.requireNonNull(paymentId, "paymentId is required");
    Objects.requireNonNull(payerVpa, "payerVpa is required");
    Objects.requireNonNull(payeeVpa, "payeeVpa is required");
    Objects.requireNonNull(amount, "amount is required");
    Objects.requireNonNull(currency, "currency is required");
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(decisionCode, "decisionCode is required");
    Objects.requireNonNull(decisionMessage, "decisionMessage is required");
    Objects.requireNonNull(decidedAt, "decidedAt is required");
  }
}

