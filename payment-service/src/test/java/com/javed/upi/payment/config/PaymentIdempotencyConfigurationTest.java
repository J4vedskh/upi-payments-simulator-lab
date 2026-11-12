package com.javed.upi.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.javed.upi.payment.service.PaymentIdempotencyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentIdempotencyConfigurationTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(PaymentIdempotencyConfiguration.class);
  private final ApplicationContextRunner configDataContextRunner = contextRunner
      .withInitializer(new ConfigDataApplicationContextInitializer());

  @Test
  void applicationDefaultsCreateABoundedExpiringStore() {
    configDataContextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      PaymentIdempotencyProperties properties = context.getBean(PaymentIdempotencyProperties.class);

      assertThat(properties.maxEntries()).isEqualTo(10_000);
      assertThat(properties.retention()).isEqualTo(Duration.ofHours(24));
      assertThat(context).hasSingleBean(PaymentIdempotencyStore.class);
    });
  }

  @Test
  void environmentOverridesBindIntoTheStoreConfiguration() {
    configDataContextRunner.withPropertyValues(
        "UPI_IDEMPOTENCY_MAX_ENTRIES=250",
        "UPI_IDEMPOTENCY_RETENTION=45m"
    ).run(context -> {
      assertThat(context).hasNotFailed();
      PaymentIdempotencyProperties properties = context.getBean(PaymentIdempotencyProperties.class);

      assertThat(properties.maxEntries()).isEqualTo(250);
      assertThat(properties.retention()).isEqualTo(Duration.ofMinutes(45));
    });
  }

  @Test
  void invalidCapacityAndRetentionFailStartup() {
    assertInvalid("must be greater than or equal to 1", "app.idempotency.max-entries=0");
    assertInvalid("retention must be greater than zero", "app.idempotency.retention=0s");
  }

  private void assertInvalid(String expectedMessage, String... properties) {
    configDataContextRunner.withPropertyValues(properties).run(context -> {
      assertThat(context).hasFailed();
      assertThat(failureChain(context.getStartupFailure())).contains(expectedMessage);
    });
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
