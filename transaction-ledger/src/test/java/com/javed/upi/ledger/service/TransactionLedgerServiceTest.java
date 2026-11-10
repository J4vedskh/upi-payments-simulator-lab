package com.javed.upi.ledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.ledger.observability.LedgerRecordMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TransactionLedgerServiceTest {
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TransactionLedgerService service = new TransactionLedgerService(
      new LedgerRecordMetrics(meterRegistry),
      Clock.fixed(Instant.parse("2026-05-27T10:05:00Z"), ZoneOffset.UTC));

  @Test
  void recordsPaymentEventAsLedgerEntry() {
    LedgerEntry entry = service.record(event("pay_123", PaymentStatus.ACCEPTED));

    assertEquals("pay_123", entry.paymentId());
    assertEquals(PaymentStatus.ACCEPTED, service.find("pay_123").status());
    assertEquals(1, service.all().size());
    assertEquals(
        1.0,
        meterRegistry.get(LedgerRecordMetrics.METRIC_NAME)
            .tag("status", "accepted")
            .counter()
            .count());
  }

  @Test
  void replayedPaymentKeepsTheFirstEntryAndCountsOnlyTheInitialRecord() {
    Instant firstRecordedAt = Instant.parse("2026-05-27T10:05:00Z");
    Clock replayClock = mock(Clock.class);
    when(replayClock.instant())
        .thenReturn(firstRecordedAt, Instant.parse("2026-05-27T10:10:00Z"));
    TransactionLedgerService replayService = new TransactionLedgerService(
        new LedgerRecordMetrics(meterRegistry),
        replayClock);

    LedgerEntry initial = replayService.record(event("pay_replayed", PaymentStatus.ACCEPTED));
    LedgerEntry replay = replayService.record(event("pay_replayed", PaymentStatus.REJECTED));

    assertEquals(initial, replay);
    assertEquals(1, replayService.all().size());
    assertEquals(PaymentStatus.ACCEPTED, replayService.find("pay_replayed").status());
    assertEquals(firstRecordedAt, replay.recordedAt());
    assertEquals(
        1.0,
        meterRegistry.get(LedgerRecordMetrics.METRIC_NAME)
            .tag("status", "accepted")
            .counter()
            .count());
    assertEquals(
        0.0,
        meterRegistry.get(LedgerRecordMetrics.METRIC_NAME)
            .tag("status", "rejected")
            .counter()
            .count());
  }

  @Test
  void missingLedgerEntryReturnsNotFound() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.find("pay_missing"));

    assertEquals(404, exception.getStatusCode().value());
    assertEquals("Ledger entry not found.", exception.getReason());
  }

  private static PaymentEvent event(String paymentId, PaymentStatus status) {
    return new PaymentEvent(
        paymentId,
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        status,
        "APPROVED",
        "Payment passed all configured fraud checks.",
        Instant.parse("2026-05-27T10:00:00Z"));
  }
}

