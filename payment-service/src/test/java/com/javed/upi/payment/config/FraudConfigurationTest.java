package com.javed.upi.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.javed.upi.events.PaymentCommand;
import com.javed.upi.fraud.FraudDecision;
import com.javed.upi.fraud.FraudEngine;
import com.javed.upi.fraud.PaymentRisk;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FraudConfigurationTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(FraudConfiguration.class);
  private final ApplicationContextRunner configDataContextRunner = contextRunner
      .withInitializer(new ConfigDataApplicationContextInitializer());

  @Test
  void defaultThresholdsPreserveExistingDecisions() {
    configDataContextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      FraudEngine engine = context.getBean(FraudEngine.class);

      assertThat(evaluate(engine, "49999.99").risk()).isEqualTo(PaymentRisk.APPROVED);
      assertThat(evaluate(engine, "50000.00").risk()).isEqualTo(PaymentRisk.REVIEW);
      assertThat(evaluate(engine, "200000.00").risk()).isEqualTo(PaymentRisk.BLOCKED);
    });
  }

  @Test
  void customThresholdsBindIntoTheFraudEngine() {
    contextRunner.withPropertyValues(
        "app.fraud.review-threshold=1000.00",
        "app.fraud.block-threshold=5000.00"
    ).run(context -> {
      assertThat(context).hasNotFailed();
      FraudEngine engine = context.getBean(FraudEngine.class);

      assertThat(evaluate(engine, "1000.00").risk()).isEqualTo(PaymentRisk.REVIEW);
      assertThat(evaluate(engine, "5000.00").risk()).isEqualTo(PaymentRisk.BLOCKED);
    });
  }

  @Test
  void applicationYamlEnvironmentOverridesBindIntoTheFraudEngine() {
    configDataContextRunner.withPropertyValues(
        "UPI_FRAUD_REVIEW_THRESHOLD=1250.00",
        "UPI_FRAUD_BLOCK_THRESHOLD=6000.00"
    ).run(context -> {
      assertThat(context).hasNotFailed();
      FraudEngine engine = context.getBean(FraudEngine.class);

      assertThat(evaluate(engine, "1250.00").risk()).isEqualTo(PaymentRisk.REVIEW);
      assertThat(evaluate(engine, "6000.00").risk()).isEqualTo(PaymentRisk.BLOCKED);
    });
  }

  @Test
  void nonPositiveThresholdFailsStartup() {
    assertInvalid(
        "must be greater than 0",
        "app.fraud.review-threshold=0");
    assertInvalid(
        "must be greater than 0",
        "app.fraud.block-threshold=-1");
  }

  @Test
  void blankThresholdFailsStartup() {
    assertInvalid(
        "must not be null",
        "app.fraud.review-threshold=");
    assertInvalid(
        "must not be null",
        "app.fraud.block-threshold=");
  }

  @Test
  void reviewThresholdMustRemainBelowBlockThreshold() {
    assertInvalid(
        "review threshold must be less than block threshold",
        "app.fraud.review-threshold=5000.00",
        "app.fraud.block-threshold=5000.00");
  }

  private void assertInvalid(String expectedMessage, String... properties) {
    configDataContextRunner.withPropertyValues(properties).run(context -> {
      assertThat(context).hasFailed();
      assertThat(failureChain(context.getStartupFailure())).contains(expectedMessage);
    });
  }

  private FraudDecision evaluate(FraudEngine engine, String amount) {
    return engine.evaluate(new PaymentCommand(
        "pay_threshold_test",
        "alice@upi",
        "merchant@upi",
        new BigDecimal(amount),
        "INR",
        "MOBILE",
        Instant.parse("2026-10-21T05:30:00Z")));
  }

  private String failureChain(Throwable failure) {
    List<String> messages = new ArrayList<>();
    Throwable current = failure;
    while (current != null) {
      messages.add(current.toString());
      current = current.getCause();
    }
    return String.join("\n", messages);
  }
}
