package com.javed.upi.payment.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentPublicationMetrics {
  public static final String METRIC_NAME = "upi.payment.publications";

  private final Counter successfulInvocations;
  private final Counter failedInvocations;

  public PaymentPublicationMetrics(MeterRegistry meterRegistry) {
    successfulInvocations = counter(meterRegistry, "success");
    failedInvocations = counter(meterRegistry, "failure");
  }

  public void recordSuccess() {
    successfulInvocations.increment();
  }

  public void recordFailure() {
    failedInvocations.increment();
  }

  private static Counter counter(MeterRegistry meterRegistry, String outcome) {
    return Counter.builder(METRIC_NAME)
        .description("Synchronous payment event publisher invocations by outcome.")
        .tag("outcome", outcome)
        .register(meterRegistry);
  }
}
