package com.javed.upi.payment.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.idempotency")
public record PaymentIdempotencyProperties(
    @Min(1) long maxEntries,
    @NotNull Duration retention) {

  @AssertTrue(message = "retention must be greater than zero")
  public boolean isRetentionValid() {
    return retention == null || (!retention.isZero() && !retention.isNegative());
  }
}
