package com.javed.upi.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.javed.upi.events.PaymentStatus;
import com.javed.upi.payment.api.PaymentResponse;
import com.javed.upi.payment.config.PaymentIdempotencyProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PaymentIdempotencyStoreTest {

  @Test
  void directConstructionRejectsInvalidLimits() {
    assertThatThrownBy(() -> new PaymentIdempotencyStore(
        new PaymentIdempotencyProperties(0, Duration.ofHours(1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxEntries must be greater than or equal to 1");
    assertThatThrownBy(() -> new PaymentIdempotencyStore(
        new PaymentIdempotencyProperties(1, Duration.ZERO)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retention must be greater than zero");
  }

  @Test
  void replayDoesNotExtendTheWriteBasedRetentionWindow() {
    AtomicLong tickerNanos = new AtomicLong();
    PaymentIdempotencyStore store = new PaymentIdempotencyStore(
        new PaymentIdempotencyProperties(10, Duration.ofMinutes(30)),
        tickerNanos::get);
    AtomicInteger creations = new AtomicInteger();

    PaymentResponse initial = store.execute(
        "hashed-key",
        "fingerprint",
        () -> response("pay_initial", creations.incrementAndGet()));
    tickerNanos.addAndGet(Duration.ofMinutes(29).toNanos());
    PaymentResponse replay = store.execute(
        "hashed-key",
        "fingerprint",
        () -> response("pay_unexpected", creations.incrementAndGet()));
    tickerNanos.addAndGet(Duration.ofMinutes(2).toNanos());
    PaymentResponse afterExpiry = store.execute(
        "hashed-key",
        "fingerprint",
        () -> response("pay_after_expiry", creations.incrementAndGet()));

    assertThat(replay).isEqualTo(initial);
    assertThat(afterExpiry.paymentId()).isEqualTo("pay_after_expiry");
    assertThat(creations).hasValue(2);
  }

  @Test
  void capacityEvictionBoundsEntriesAndEvictedKeyBecomesNew() {
    PaymentIdempotencyStore store = new PaymentIdempotencyStore(
        new PaymentIdempotencyProperties(1, Duration.ofHours(1)),
        System::nanoTime);
    store.execute("first-key", "first-fingerprint", () -> response("pay_first", 1));
    store.execute("second-key", "second-fingerprint", () -> response("pay_second", 2));
    store.cleanUp();

    assertThat(store.estimatedSize()).isEqualTo(1);
    String evictedKey = store.contains("first-key") ? "second-key" : "first-key";
    String evictedFingerprint = evictedKey.equals("first-key")
        ? "first-fingerprint"
        : "second-fingerprint";
    AtomicInteger newCreation = new AtomicInteger();

    PaymentResponse recreated = store.execute(
        evictedKey,
        evictedFingerprint,
        () -> response("pay_recreated", newCreation.incrementAndGet()));

    assertThat(recreated.paymentId()).isEqualTo("pay_recreated");
    assertThat(newCreation).hasValue(1);
  }

  private static PaymentResponse response(String paymentId, int sequence) {
    return new PaymentResponse(
        paymentId,
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        PaymentStatus.ACCEPTED,
        "APPROVED",
        "Payment passed all configured fraud checks.",
        Instant.parse("2026-11-12T05:00:00Z").plusSeconds(sequence));
  }
}
