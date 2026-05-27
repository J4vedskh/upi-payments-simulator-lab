package com.javed.upi.payment.service;

import com.javed.upi.events.PaymentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {
  private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
  private final String topic;

  public KafkaPaymentEventPublisher(
      KafkaTemplate<String, PaymentEvent> kafkaTemplate,
      @Value("${app.kafka.topics.payment-events:payment-events}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Override
  public void publish(PaymentEvent event) {
    kafkaTemplate.send(topic, event.paymentId(), event);
  }
}

