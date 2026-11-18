package com.javed.upi.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentAcknowledgementMetricsTest {

  @Test
  void preRegistersOnlyBoundedOutcomeSeriesAndRecordsAcknowledgements() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PaymentAcknowledgementMetrics metrics = new PaymentAcknowledgementMetrics(registry);

    assertThat(registry.find(PaymentAcknowledgementMetrics.METRIC_NAME).counters())
        .hasSize(2);
    for (String outcome : new String[] {"success", "failure"}) {
      Counter counter = registry.get(PaymentAcknowledgementMetrics.METRIC_NAME)
          .tag("outcome", outcome)
          .counter();
      assertThat(counter.count()).isZero();
      assertThat(counter.getId().getTags()).singleElement().satisfies(tag -> {
        assertThat(tag.getKey()).isEqualTo("outcome");
        assertThat(tag.getValue()).isEqualTo(outcome);
      });
    }

    metrics.recordSuccess();
    metrics.recordFailure();

    assertThat(Map.of(
        "success", registry.get(PaymentAcknowledgementMetrics.METRIC_NAME)
            .tag("outcome", "success").counter().count(),
        "failure", registry.get(PaymentAcknowledgementMetrics.METRIC_NAME)
            .tag("outcome", "failure").counter().count()))
        .containsEntry("success", 1.0)
        .containsEntry("failure", 1.0);
  }
}
