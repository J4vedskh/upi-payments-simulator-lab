package com.javed.upi.payment.service;

import com.javed.upi.events.PaymentEvent;

public interface PaymentEventPublisher {
  void publish(PaymentEvent event);
}

