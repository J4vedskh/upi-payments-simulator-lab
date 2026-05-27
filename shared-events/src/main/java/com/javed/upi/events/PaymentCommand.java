package com.javed.upi.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PaymentCommand(
    String paymentId,
    String payerVpa,
    String payeeVpa,
    BigDecimal amount,
    String currency,
    String channel,
    Instant requestedAt) {

  public PaymentCommand {
    Objects.requireNonNull(paymentId, "paymentId is required");
    Objects.requireNonNull(payerVpa, "payerVpa is required");
    Objects.requireNonNull(payeeVpa, "payeeVpa is required");
    Objects.requireNonNull(amount, "amount is required");
    Objects.requireNonNull(currency, "currency is required");
    Objects.requireNonNull(channel, "channel is required");
    Objects.requireNonNull(requestedAt, "requestedAt is required");
  }
}

