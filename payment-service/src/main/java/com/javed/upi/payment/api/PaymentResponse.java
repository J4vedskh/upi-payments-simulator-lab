package com.javed.upi.payment.api;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    String paymentId,
    String payerVpa,
    String payeeVpa,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String decisionCode,
    String decisionMessage,
    Instant decidedAt) {

  public static PaymentResponse from(PaymentEvent event) {
    return new PaymentResponse(
        event.paymentId(),
        event.payerVpa(),
        event.payeeVpa(),
        event.amount(),
        event.currency(),
        event.status(),
        event.decisionCode(),
        event.decisionMessage(),
        event.decidedAt());
  }
}

