package com.javed.upi.ledger.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.javed.upi.events.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LedgerRecordMetricsTest {

  @Test
  void preRegistersOnlyBoundedStatusSeriesAndRecordsEvents() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LedgerRecordMetrics metrics = new LedgerRecordMetrics(registry);

    assertThat(registry.find(LedgerRecordMetrics.METRIC_NAME).counters())
        .hasSize(PaymentStatus.values().length);
    for (PaymentStatus status : PaymentStatus.values()) {
      Counter counter = registry.get(LedgerRecordMetrics.METRIC_NAME)
          .tag("status", status.name().toLowerCase(Locale.ROOT))
          .counter();
      assertThat(counter.count()).isZero();
      assertThat(counter.getId().getTags()).singleElement().satisfies(tag -> {
        assertThat(tag.getKey()).isEqualTo("status");
        assertThat(tag.getValue()).isEqualTo(status.name().toLowerCase(Locale.ROOT));
      });
    }

    metrics.record(PaymentStatus.REJECTED);

    assertThat(registry.get(LedgerRecordMetrics.METRIC_NAME)
        .tag("status", "rejected")
        .counter()
        .count()).isEqualTo(1.0);
  }
}
