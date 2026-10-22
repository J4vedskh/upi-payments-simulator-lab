package com.javed.upi.payment.observability;

import com.javed.upi.events.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PaymentDecisionMetrics {
  public static final String METRIC_NAME = "upi.payment.decisions";

  private final Map<PaymentStatus, Counter> decisions;

  public PaymentDecisionMetrics(MeterRegistry meterRegistry) {
    decisions = new EnumMap<>(PaymentStatus.class);
    for (PaymentStatus status : PaymentStatus.values()) {
      decisions.put(status, Counter.builder(METRIC_NAME)
          .description("Payment decisions evaluated by the fraud engine.")
          .tag("status", status.name().toLowerCase(Locale.ROOT))
          .register(meterRegistry));
    }
  }

  public void record(PaymentStatus status) {
    decisions.get(Objects.requireNonNull(status, "status is required")).increment();
  }
}
