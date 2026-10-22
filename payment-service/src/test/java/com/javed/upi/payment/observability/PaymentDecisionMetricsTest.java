package com.javed.upi.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.javed.upi.events.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentDecisionMetricsTest {

  @Test
  void preRegistersOnlyBoundedStatusSeriesAndRecordsDecisions() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PaymentDecisionMetrics metrics = new PaymentDecisionMetrics(registry);

    assertThat(registry.find(PaymentDecisionMetrics.METRIC_NAME).counters())
        .hasSize(PaymentStatus.values().length);
    for (PaymentStatus status : PaymentStatus.values()) {
      Counter counter = registry.get(PaymentDecisionMetrics.METRIC_NAME)
          .tag("status", status.name().toLowerCase(Locale.ROOT))
          .counter();
      assertThat(counter.count()).isZero();
      assertThat(counter.getId().getTags()).singleElement().satisfies(tag -> {
        assertThat(tag.getKey()).isEqualTo("status");
        assertThat(tag.getValue()).isEqualTo(status.name().toLowerCase(Locale.ROOT));
      });
    }

    metrics.record(PaymentStatus.REVIEW);

    assertThat(registry.get(PaymentDecisionMetrics.METRIC_NAME)
        .tag("status", "review")
        .counter()
        .count()).isEqualTo(1.0);
  }
}
