package com.javed.upi.fraud;

import java.util.List;

public record FraudDecision(
    PaymentRisk risk,
    String code,
    String message,
    List<FraudFinding> findings) {

  public static FraudDecision approved() {
    return new FraudDecision(PaymentRisk.APPROVED, "APPROVED", "Payment passed all configured fraud checks.", List.of());
  }

  public boolean isBlocked() {
    return risk == PaymentRisk.BLOCKED;
  }
}

