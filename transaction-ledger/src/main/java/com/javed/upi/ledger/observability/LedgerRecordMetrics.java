package com.javed.upi.ledger.observability;

import com.javed.upi.events.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class LedgerRecordMetrics {
  public static final String METRIC_NAME = "upi.ledger.records";

  private final Map<PaymentStatus, Counter> records;

  public LedgerRecordMetrics(MeterRegistry meterRegistry) {
    records = new EnumMap<>(PaymentStatus.class);
    for (PaymentStatus status : PaymentStatus.values()) {
      records.put(status, Counter.builder(METRIC_NAME)
          .description("Payment events recorded by the transaction ledger.")
          .tag("status", status.name().toLowerCase(Locale.ROOT))
          .register(meterRegistry));
    }
  }

  public void record(PaymentStatus status) {
    records.get(Objects.requireNonNull(status, "status is required")).increment();
  }
}
