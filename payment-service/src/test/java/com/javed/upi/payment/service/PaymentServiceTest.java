package com.javed.upi.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.fraud.FraudEngine;
import com.javed.upi.payment.api.PaymentRequest;
import com.javed.upi.payment.api.PaymentResponse;
import com.javed.upi.payment.config.PaymentIdempotencyProperties;
import com.javed.upi.payment.observability.PaymentDecisionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {
  private final CapturingPublisher publisher = new CapturingPublisher();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final PaymentService service = new PaymentService(
      FraudEngine.defaultEngine(),
      publisher,
      new PaymentDecisionMetrics(meterRegistry),
      Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC),
      idempotencyStore());

  @Test
  void acceptedPaymentIsStoredAndPublished() {
    PaymentResponse response = service.submit(new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "inr",
        "mobile"), null);

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
        "MOBILE"), null);

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
        "MOBILE"), null);

    assertEquals(PaymentStatus.REVIEW, response.status());
    assertEquals(1.0, decisionCount(PaymentStatus.REVIEW));
  }

  @Test
  void equivalentRetryReturnsTheOriginalDecisionWithoutRepublishing() {
    PaymentResponse initial = service.submit(new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "inr",
        "mobile"), "retry-key-123");

    PaymentResponse replay = service.submit(new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.500"),
        "INR",
        "MOBILE"), "retry-key-123");

    assertEquals(initial, replay);
    assertEquals(1, publisher.events.size());
    assertEquals(1.0, decisionCount(PaymentStatus.ACCEPTED));
  }

  @Test
  void requestsWithoutAnIdempotencyKeyRemainIndependent() {
    PaymentRequest request = new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        "MOBILE");

    PaymentResponse first = service.submit(request, null);
    PaymentResponse second = service.submit(request, null);

    assertNotEquals(first.paymentId(), second.paymentId());
    assertEquals(2, publisher.events.size());
    assertEquals(2.0, decisionCount(PaymentStatus.ACCEPTED));
  }

  @Test
  void concurrentEquivalentRetriesPublishOnlyOnePayment() throws Exception {
    BlockingPublisher blockingPublisher = new BlockingPublisher();
    SimpleMeterRegistry concurrentRegistry = new SimpleMeterRegistry();
    PaymentService concurrentService = new PaymentService(
        FraudEngine.defaultEngine(),
        blockingPublisher,
        new PaymentDecisionMetrics(concurrentRegistry),
        Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC),
        idempotencyStore());
    PaymentRequest request = new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        "MOBILE");
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<PaymentResponse> first = executor.submit(
          () -> concurrentService.submit(request, "concurrent-retry-key"));
      assertTrue(blockingPublisher.publishStarted.await(5, TimeUnit.SECONDS));
      Future<PaymentResponse> second = executor.submit(
          () -> concurrentService.submit(request, "concurrent-retry-key"));

      blockingPublisher.releasePublish.countDown();

      assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
      assertEquals(1, blockingPublisher.publishCount.get());
      assertEquals(
          1.0,
          concurrentRegistry.get(PaymentDecisionMetrics.METRIC_NAME)
              .tag("status", "accepted")
              .counter()
              .count());
    } finally {
      blockingPublisher.releasePublish.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void changedRequestForAnExistingKeyReturnsAConflictWithoutSideEffects() {
    service.submit(new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        "MOBILE"), "retry-key-456");

    PaymentIdempotencyException exception = assertThrows(
        PaymentIdempotencyException.class,
        () -> service.submit(new PaymentRequest(
            "alice@upi",
            "merchant@upi",
            new BigDecimal("250.00"),
            "INR",
            "MOBILE"), "retry-key-456"));

    assertEquals(PaymentIdempotencyException.Reason.CONFLICT, exception.reason());
    assertEquals(1, publisher.events.size());
    assertEquals(1.0, decisionCount(PaymentStatus.ACCEPTED));
  }

  @Test
  void invalidIdempotencyKeysAreRejectedBeforePaymentSideEffects() {
    PaymentRequest request = new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        "MOBILE");

    for (String key : List.of(" ", "bad key", "x".repeat(129), "bad\u007fkey")) {
      PaymentIdempotencyException exception = assertThrows(
          PaymentIdempotencyException.class,
          () -> service.submit(request, key));
      assertEquals(PaymentIdempotencyException.Reason.INVALID_KEY, exception.reason());
    }

    assertEquals(0, publisher.events.size());
    assertEquals(0.0, decisionCount(PaymentStatus.ACCEPTED));
  }

  @Test
  void synchronousPublicationFailureLeavesNoLocalSuccessAndAllowsKeyedRetry() {
    FailOncePublisher failOncePublisher = new FailOncePublisher();
    SimpleMeterRegistry failureRegistry = new SimpleMeterRegistry();
    PaymentService failureService = new PaymentService(
        FraudEngine.defaultEngine(),
        failOncePublisher,
        new PaymentDecisionMetrics(failureRegistry),
        Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC),
        idempotencyStore());
    PaymentRequest request = new PaymentRequest(
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        "MOBILE");

    PaymentEventPublicationException failure = assertThrows(
        PaymentEventPublicationException.class,
        () -> failureService.submit(request, "retry-after-publish-failure"));

    assertEquals("Payment event publication is temporarily unavailable.", failure.getMessage());
    assertThrows(
        ResponseStatusException.class,
        () -> failureService.find(failOncePublisher.failedPaymentId));
    assertEquals(
        0.0,
        failureRegistry.get(PaymentDecisionMetrics.METRIC_NAME)
            .tag("status", "accepted")
            .counter()
            .count());

    PaymentResponse retry = failureService.submit(request, "retry-after-publish-failure");
    PaymentResponse replay = failureService.submit(request, "retry-after-publish-failure");

    assertEquals(retry, replay);
    assertEquals(2, failOncePublisher.attempts.get());
    assertEquals(1, failOncePublisher.publishedEvents.size());
    assertEquals(
        1.0,
        failureRegistry.get(PaymentDecisionMetrics.METRIC_NAME)
            .tag("status", "accepted")
            .counter()
            .count());
  }

  @Test
  void missingPaymentReturnsNotFound() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.find("pay_missing"));

    assertEquals(404, exception.getStatusCode().value());
    assertEquals("Payment not found.", exception.getReason());
  }

  private double decisionCount(PaymentStatus status) {
    return meterRegistry.get(PaymentDecisionMetrics.METRIC_NAME)
        .tag("status", status.name().toLowerCase(Locale.ROOT))
        .counter()
        .count();
  }

  private static PaymentIdempotencyStore idempotencyStore() {
    return new PaymentIdempotencyStore(
        new PaymentIdempotencyProperties(10_000, java.time.Duration.ofHours(24)));
  }

  private static final class CapturingPublisher implements PaymentEventPublisher {
    private final List<PaymentEvent> events = new ArrayList<>();

    @Override
    public void publish(PaymentEvent event) {
      events.add(event);
    }
  }

  private static final class BlockingPublisher implements PaymentEventPublisher {
    private final CountDownLatch publishStarted = new CountDownLatch(1);
    private final CountDownLatch releasePublish = new CountDownLatch(1);
    private final AtomicInteger publishCount = new AtomicInteger();

    @Override
    public void publish(PaymentEvent event) {
      publishCount.incrementAndGet();
      publishStarted.countDown();
      try {
        if (!releasePublish.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release the test publisher.");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting to release the test publisher.", exception);
      }
    }
  }

  private static final class FailOncePublisher implements PaymentEventPublisher {
    private final AtomicInteger attempts = new AtomicInteger();
    private final List<PaymentEvent> publishedEvents = new ArrayList<>();
    private String failedPaymentId;

    @Override
    public void publish(PaymentEvent event) {
      if (attempts.incrementAndGet() == 1) {
        failedPaymentId = event.paymentId();
        throw new IllegalStateException("broker.internal.example:9092 refused the request");
      }
      publishedEvents.add(event);
    }
  }
}

