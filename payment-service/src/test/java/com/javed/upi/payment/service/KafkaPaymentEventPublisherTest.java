package com.javed.upi.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.payment.observability.PaymentAcknowledgementMetrics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@SuppressWarnings("unchecked")
class KafkaPaymentEventPublisherTest {
  private static final String TOPIC = "payment-events";

  @Test
  void recordsSuccessOnlyAfterTheKafkaFutureAcknowledges() {
    KafkaTemplate<String, PaymentEvent> kafkaTemplate = mock(KafkaTemplate.class);
    PaymentAcknowledgementMetrics metrics = mock(PaymentAcknowledgementMetrics.class);
    CompletableFuture<SendResult<String, PaymentEvent>> sendFuture = new CompletableFuture<>();
    PaymentEvent event = event();
    when(kafkaTemplate.send(TOPIC, event.paymentId(), event)).thenReturn(sendFuture);
    KafkaPaymentEventPublisher publisher = new KafkaPaymentEventPublisher(
        kafkaTemplate,
        TOPIC,
        metrics);

    publisher.publish(event);

    assertThat(sendFuture).isNotDone();
    verifyNoInteractions(metrics);

    sendFuture.complete(mock(SendResult.class));

    verify(metrics).recordSuccess();
    verify(metrics, never()).recordFailure();
  }

  @Test
  void recordsFailureWithoutLettingMetricErrorsEscapeTheCallback() {
    KafkaTemplate<String, PaymentEvent> kafkaTemplate = mock(KafkaTemplate.class);
    PaymentAcknowledgementMetrics metrics = mock(PaymentAcknowledgementMetrics.class);
    doThrow(new IllegalStateException("metrics backend unavailable"))
        .when(metrics).recordFailure();
    CompletableFuture<SendResult<String, PaymentEvent>> sendFuture = new CompletableFuture<>();
    PaymentEvent event = event();
    when(kafkaTemplate.send(TOPIC, event.paymentId(), event)).thenReturn(sendFuture);
    KafkaPaymentEventPublisher publisher = new KafkaPaymentEventPublisher(
        kafkaTemplate,
        TOPIC,
        metrics);

    publisher.publish(event);
    boolean completed = sendFuture.completeExceptionally(
        new IllegalStateException("broker.internal.example:9092 acknowledgement failed"));

    assertThat(completed).isTrue();
    verify(metrics).recordFailure();
    verify(metrics, never()).recordSuccess();
    verify(kafkaTemplate).send(TOPIC, event.paymentId(), event);
  }

  private static PaymentEvent event() {
    return new PaymentEvent(
        "pay_ack_test",
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        PaymentStatus.ACCEPTED,
        "APPROVED",
        "Payment passed all configured fraud checks.",
        Instant.parse("2026-11-18T05:00:00Z"));
  }
}
