package com.javed.upi.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.fraud.FraudEngine;
import com.javed.upi.payment.api.PaymentRequest;
import com.javed.upi.payment.api.PaymentResponse;
import com.javed.upi.payment.observability.PaymentDecisionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {
  private final CapturingPublisher publisher = new CapturingPublisher();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final PaymentService service = new PaymentService(
      FraudEngine.defaultEngine(),
      publisher,
      new PaymentDecisionMetrics(meterRegistry),
      Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC));

  @Test
  void acceptedPaymentIsStoredAndPublished() {
    PaymentResponse response = service.submit(new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "inr",
        "mobile"));

    assertEquals(PaymentStatus.ACCEPTED, response.status());
    assertEquals(response.paymentId(), service.find(response.paymentId()).paymentId());
    assertEquals(1, publisher.events.size());
    assertEquals(response.paymentId(), publisher.events.get(0).paymentId());
    assertEquals(1.0, decisionCount(PaymentStatus.ACCEPTED));
  }

  @Test
  void blockedPaymentIsRejectedButStillAuditable() {
    PaymentResponse response = service.submit(new PaymentRequest(
        "alice@upi",
        "alice@upi",
        new BigDecimal("100.00"),
        "INR",
        "MOBILE"));

    assertEquals(PaymentStatus.REJECTED, response.status());
    assertEquals("SELF_TRANSFER", response.decisionCode());
    assertEquals(1, publisher.events.size());
    assertEquals(1.0, decisionCount(PaymentStatus.REJECTED));
  }

  @Test
  void reviewPaymentIncrementsTheBoundedReviewMetric() {
    PaymentResponse response = service.submit(new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("50000.00"),
        "INR",
        "MOBILE"));

    assertEquals(PaymentStatus.REVIEW, response.status());
    assertEquals(1.0, decisionCount(PaymentStatus.REVIEW));
  }

  @Test
  void missingPaymentReturnsNotFound() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.find("pay_missing"));

    assertTrue(exception.getMessage().contains("Payment not found"));
  }

  private double decisionCount(PaymentStatus status) {
    return meterRegistry.get(PaymentDecisionMetrics.METRIC_NAME)
        .tag("status", status.name().toLowerCase(Locale.ROOT))
        .counter()
        .count();
  }

  private static final class CapturingPublisher implements PaymentEventPublisher {
    private final List<PaymentEvent> events = new ArrayList<>();

    @Override
    public void publish(PaymentEvent event) {
      events.add(event);
    }
  }
}

