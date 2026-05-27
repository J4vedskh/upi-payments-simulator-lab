package com.javed.upi.ledger.service;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntry(
    String paymentId,
    String payerVpa,
    String payeeVpa,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String decisionCode,
    Instant recordedAt) {

  public static LedgerEntry from(PaymentEvent event, Instant recordedAt) {
    return new LedgerEntry(
        event.paymentId(),
        event.payerVpa(),
        event.payeeVpa(),
        event.amount(),
        event.currency(),
        event.status(),
        event.decisionCode(),
        recordedAt);
  }
}

