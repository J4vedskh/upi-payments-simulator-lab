package com.javed.upi.payment.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.fraud")
public record FraudThresholdProperties(
    @NotNull @Positive BigDecimal reviewThreshold,
    @NotNull @Positive BigDecimal blockThreshold) {

  @AssertTrue(message = "review threshold must be less than block threshold")
  public boolean isThresholdOrderValid() {
    return reviewThreshold == null
        || blockThreshold == null
        || reviewThreshold.compareTo(blockThreshold) < 0;
  }
}
