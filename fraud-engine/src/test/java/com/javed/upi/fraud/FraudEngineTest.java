package com.javed.upi.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

