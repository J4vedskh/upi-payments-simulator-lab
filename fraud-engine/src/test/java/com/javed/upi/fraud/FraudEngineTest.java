package com.javed.upi.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.javed.upi.events.PaymentCommand;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FraudEngineTest {
  private final FraudEngine fraudEngine = FraudEngine.defaultEngine();

  @Test
  void approvesNormalInrPayment() {
    FraudDecision decision = fraudEngine.evaluate(command("alice@upi", "merchant@upi", "499.00", "INR"));

    assertEquals(PaymentRisk.APPROVED, decision.risk());
    assertFalse(decision.isBlocked());
  }

  @Test
  void blocksSelfTransfer() {
    FraudDecision decision = fraudEngine.evaluate(command("alice@upi", "alice@upi", "100.00", "INR"));

    assertEquals(PaymentRisk.BLOCKED, decision.risk());
    assertEquals("SELF_TRANSFER", decision.code());
  }

  @Test
  void sendsHighValuePaymentForReview() {
    FraudDecision decision = fraudEngine.evaluate(command("alice@upi", "merchant@upi", "75000.00", "INR"));

    assertEquals(PaymentRisk.REVIEW, decision.risk());
    assertEquals("HIGH_VALUE_PAYMENT", decision.code());
  }

  @Test
  void customThresholdsApplyAtExactBoundaries() {
    FraudEngine configuredEngine = FraudEngine.withAmountThresholds(
        new BigDecimal("1000.00"),
        new BigDecimal("5000.00"));
    FraudDecision approved = configuredEngine.evaluate(
        command("alice@upi", "merchant@upi", "999.99", "INR"));
    FraudDecision review = configuredEngine.evaluate(
        command("alice@upi", "merchant@upi", "1000.00", "INR"));
    FraudDecision blocked = configuredEngine.evaluate(
        command("alice@upi", "merchant@upi", "5000.00", "INR"));

    assertEquals(PaymentRisk.APPROVED, approved.risk());
    assertEquals(PaymentRisk.REVIEW, review.risk());
    assertEquals("HIGH_VALUE_PAYMENT", review.code());
    assertEquals(PaymentRisk.BLOCKED, blocked.risk());
    assertEquals("LIMIT_EXCEEDED", blocked.code());
    assertEquals(
        "Payment amount reached or exceeded the configured hard limit.",
        blocked.message());
  }

  @Test
  void rejectsNonPositiveOrMisorderedThresholds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FraudEngine.withAmountThresholds(BigDecimal.ZERO, new BigDecimal("5000.00")));
    assertThrows(
        IllegalArgumentException.class,
        () -> FraudEngine.withAmountThresholds(new BigDecimal("5000.00"), new BigDecimal("5000.00")));
    assertThrows(
        IllegalArgumentException.class,
        () -> FraudEngine.withAmountThresholds(new BigDecimal("6000.00"), new BigDecimal("5000.00")));
  }

  private static PaymentCommand command(String payer, String payee, String amount, String currency) {
    return new PaymentCommand(
        "pay_test_001",
        payer,
        payee,
        new BigDecimal(amount),
        currency,
        "MOBILE",
        Instant.parse("2026-05-27T10:00:00Z"));
  }
}

