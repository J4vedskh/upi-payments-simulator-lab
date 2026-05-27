package com.javed.upi.payment.service;

import com.javed.upi.events.PaymentEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!kafka")
public class InMemoryPaymentEventPublisher implements PaymentEventPublisher {
  private final List<PaymentEvent> publishedEvents = new ArrayList<>();

  @Override
  public synchronized void publish(PaymentEvent event) {
    publishedEvents.add(event);
  }

  public synchronized List<PaymentEvent> publishedEvents() {
    return List.copyOf(publishedEvents);
  }
}

