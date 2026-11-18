package com.javed.upi.payment.service;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.payment.observability.PaymentAcknowledgementMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);

  private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
  private final String topic;
  private final PaymentAcknowledgementMetrics acknowledgementMetrics;

  public KafkaPaymentEventPublisher(
      KafkaTemplate<String, PaymentEvent> kafkaTemplate,
      @Value("${app.kafka.topics.payment-events:payment-events}") String topic,
      PaymentAcknowledgementMetrics acknowledgementMetrics) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
    this.acknowledgementMetrics = acknowledgementMetrics;
  }

  @Override
  public void publish(PaymentEvent event) {
    kafkaTemplate.send(topic, event.paymentId(), event)
        .whenComplete((result, exception) -> {
          if (exception == null) {
            recordAcknowledgement("success", acknowledgementMetrics::recordSuccess);
          } else {
            recordAcknowledgement("failure", acknowledgementMetrics::recordFailure);
          }
        });
  }

  private void recordAcknowledgement(String outcome, Runnable recorder) {
    try {
      recorder.run();
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Unable to record payment acknowledgement metric for outcome={} cause={}",
          outcome,
          exception.getClass().getSimpleName());
    }
  }
}

