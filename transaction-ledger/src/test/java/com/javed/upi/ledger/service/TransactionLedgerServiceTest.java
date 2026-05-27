package com.javed.upi.ledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TransactionLedgerServiceTest {
  private final TransactionLedgerService service = new TransactionLedgerService(
      Clock.fixed(Instant.parse("2026-05-27T10:05:00Z"), ZoneOffset.UTC));

  @Test
  void recordsPaymentEventAsLedgerEntry() {
    LedgerEntry entry = service.record(event("pay_123", PaymentStatus.ACCEPTED));

    assertEquals("pay_123", entry.paymentId());
    assertEquals(PaymentStatus.ACCEPTED, service.find("pay_123").status());
    assertEquals(1, service.all().size());
  }

  @Test
  void missingLedgerEntryReturnsNotFound() {
    assertThrows(ResponseStatusException.class, () -> service.find("pay_missing"));
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

