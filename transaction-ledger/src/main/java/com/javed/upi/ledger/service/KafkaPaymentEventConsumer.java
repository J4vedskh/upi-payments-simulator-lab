package com.javed.upi.ledger.service;

import com.javed.upi.events.PaymentEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public class KafkaPaymentEventConsumer {
  private final TransactionLedgerService ledgerService;

  public KafkaPaymentEventConsumer(TransactionLedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.payment-events:payment-events}",
      groupId = "${spring.kafka.consumer.group-id:transaction-ledger}")
  public void onPaymentEvent(PaymentEvent event) {
    ledgerService.record(event);
  }
}

