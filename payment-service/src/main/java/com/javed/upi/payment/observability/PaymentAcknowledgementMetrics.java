package com.javed.upi.payment.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentAcknowledgementMetrics {
  public static final String METRIC_NAME = "upi.payment.publication.acknowledgements";

  private final Counter successfulAcknowledgements;
  private final Counter failedAcknowledgements;

  public PaymentAcknowledgementMetrics(MeterRegistry meterRegistry) {
    successfulAcknowledgements = counter(meterRegistry, "success");
    failedAcknowledgements = counter(meterRegistry, "failure");
  }

  public void recordSuccess() {
    successfulAcknowledgements.increment();
  }

  public void recordFailure() {
    failedAcknowledgements.increment();
  }

  private static Counter counter(MeterRegistry meterRegistry, String outcome) {
    return Counter.builder(METRIC_NAME)
        .description("Asynchronous Kafka payment event acknowledgements by outcome.")
        .tag("outcome", outcome)
        .register(meterRegistry);
  }
}
