package com.javed.upi.fraud;

import com.javed.upi.events.PaymentCommand;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FraudEngine {
  private static final BigDecimal REVIEW_LIMIT = new BigDecimal("50000.00");
  private static final BigDecimal BLOCK_LIMIT = new BigDecimal("200000.00");

  private final List<FraudRule> rules;

  public FraudEngine(List<FraudRule> rules) {
    this.rules = List.copyOf(rules);
  }

  public static FraudEngine defaultEngine() {
    return new FraudEngine(List.of(
        FraudEngine::blockSelfTransfer,
        FraudEngine::blockRestrictedVpa,
        FraudEngine::reviewLargePayment,
        FraudEngine::reviewNonInrPayment));
  }

  public FraudDecision evaluate(PaymentCommand command) {
    List<FraudFinding> findings = rules.stream()
        .map(rule -> rule.evaluate(command))
        .flatMap(Optional::stream)
        .sorted(Comparator.comparing(FraudFinding::risk).reversed())
        .toList();

    return findings.stream()
        .filter(finding -> finding.risk() == PaymentRisk.BLOCKED)
        .findFirst()
        .map(finding -> toDecision(PaymentRisk.BLOCKED, finding, findings))
        .or(() -> findings.stream()
            .filter(finding -> finding.risk() == PaymentRisk.REVIEW)
            .findFirst()
            .map(finding -> toDecision(PaymentRisk.REVIEW, finding, findings)))
        .orElseGet(FraudDecision::approved);
  }

  private static FraudDecision toDecision(PaymentRisk risk, FraudFinding primary, List<FraudFinding> findings) {
    return new FraudDecision(risk, primary.code(), primary.message(), findings);
  }

  private static Optional<FraudFinding> blockSelfTransfer(PaymentCommand command) {
    if (command.payerVpa().equalsIgnoreCase(command.payeeVpa())) {
      return Optional.of(new FraudFinding(
          PaymentRisk.BLOCKED,
          "SELF_TRANSFER",
          "Payer and payee VPA must be different."));
    }
    return Optional.empty();
  }

  private static Optional<FraudFinding> blockRestrictedVpa(PaymentCommand command) {
    String payee = command.payeeVpa().toLowerCase(Locale.ROOT);
    if (payee.contains("blocked") || payee.contains("risk")) {
      return Optional.of(new FraudFinding(
          PaymentRisk.BLOCKED,
          "RESTRICTED_PAYEE",
          "Payee VPA matched a restricted pattern."));
    }
    return Optional.empty();
  }

  private static Optional<FraudFinding> reviewLargePayment(PaymentCommand command) {
    if (command.amount().compareTo(BLOCK_LIMIT) >= 0) {
      return Optional.of(new FraudFinding(
          PaymentRisk.BLOCKED,
          "LIMIT_EXCEEDED",
          "Payment amount exceeds the configured hard limit."));
    }
    if (command.amount().compareTo(REVIEW_LIMIT) >= 0) {
      return Optional.of(new FraudFinding(
          PaymentRisk.REVIEW,
          "HIGH_VALUE_PAYMENT",
          "Payment amount requires manual review."));
    }
    return Optional.empty();
  }

  private static Optional<FraudFinding> reviewNonInrPayment(PaymentCommand command) {
    if (!"INR".equalsIgnoreCase(command.currency())) {
      return Optional.of(new FraudFinding(
          PaymentRisk.REVIEW,
          "NON_INR_PAYMENT",
          "Non-INR payments require review in this simulator."));
    }
    return Optional.empty();
  }
}

