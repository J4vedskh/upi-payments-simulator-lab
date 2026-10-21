package com.javed.upi.fraud;

import com.javed.upi.events.PaymentCommand;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class FraudEngine {
  public static final BigDecimal DEFAULT_REVIEW_THRESHOLD = new BigDecimal("50000.00");
  public static final BigDecimal DEFAULT_BLOCK_THRESHOLD = new BigDecimal("200000.00");

  private final List<FraudRule> rules;

  public FraudEngine(List<FraudRule> rules) {
    this.rules = List.copyOf(rules);
  }

  public static FraudEngine defaultEngine() {
    return withAmountThresholds(DEFAULT_REVIEW_THRESHOLD, DEFAULT_BLOCK_THRESHOLD);
  }

  public static FraudEngine withAmountThresholds(
      BigDecimal reviewThreshold,
      BigDecimal blockThreshold) {
    BigDecimal validatedReviewThreshold = Objects.requireNonNull(
        reviewThreshold,
        "reviewThreshold is required");
    BigDecimal validatedBlockThreshold = Objects.requireNonNull(
        blockThreshold,
        "blockThreshold is required");
    if (validatedReviewThreshold.signum() <= 0 || validatedBlockThreshold.signum() <= 0) {
      throw new IllegalArgumentException("Fraud amount thresholds must be positive.");
    }
    if (validatedReviewThreshold.compareTo(validatedBlockThreshold) >= 0) {
      throw new IllegalArgumentException(
          "Fraud review threshold must be less than the block threshold.");
    }

    return new FraudEngine(List.of(
        FraudEngine::blockSelfTransfer,
        FraudEngine::blockRestrictedVpa,
        command -> reviewLargePayment(
            command,
            validatedReviewThreshold,
            validatedBlockThreshold),
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

  private static Optional<FraudFinding> reviewLargePayment(
      PaymentCommand command,
      BigDecimal reviewThreshold,
      BigDecimal blockThreshold) {
    if (command.amount().compareTo(blockThreshold) >= 0) {
      return Optional.of(new FraudFinding(
          PaymentRisk.BLOCKED,
          "LIMIT_EXCEEDED",
          "Payment amount reached or exceeded the configured hard limit."));
    }
    if (command.amount().compareTo(reviewThreshold) >= 0) {
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

