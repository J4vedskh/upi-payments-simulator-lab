package com.javed.upi.payment.service;

import com.javed.upi.events.PaymentCommand;
import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.fraud.FraudDecision;
import com.javed.upi.fraud.FraudEngine;
import com.javed.upi.fraud.PaymentRisk;
import com.javed.upi.payment.api.PaymentRequest;
import com.javed.upi.payment.api.PaymentResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
  private final FraudEngine fraudEngine;
  private final PaymentEventPublisher eventPublisher;
  private final Clock clock;
  private final Map<String, PaymentEvent> payments = new ConcurrentHashMap<>();

  public PaymentService(FraudEngine fraudEngine, PaymentEventPublisher eventPublisher) {
    this(fraudEngine, eventPublisher, Clock.systemUTC());
  }

  PaymentService(FraudEngine fraudEngine, PaymentEventPublisher eventPublisher, Clock clock) {
    this.fraudEngine = fraudEngine;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public PaymentResponse submit(PaymentRequest request) {
    Instant now = Instant.now(clock);
    PaymentCommand command = new PaymentCommand(
        "pay_" + UUID.randomUUID(),
        request.payerVpa(),
        request.payeeVpa(),
        request.amount(),
        request.currency().toUpperCase(),
        request.channel().toUpperCase(),
        now);

    FraudDecision decision = fraudEngine.evaluate(command);
    PaymentEvent event = new PaymentEvent(
        command.paymentId(),
        command.payerVpa(),
        command.payeeVpa(),
        command.amount(),
        command.currency(),
        toStatus(decision.risk()),
        decision.code(),
        decision.message(),
        now);

    payments.put(event.paymentId(), event);
    eventPublisher.publish(event);
    return PaymentResponse.from(event);
  }

  public PaymentResponse find(String paymentId) {
    PaymentEvent event = payments.get(paymentId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId);
    }
    return PaymentResponse.from(event);
  }

  private static PaymentStatus toStatus(PaymentRisk risk) {
    return switch (risk) {
      case APPROVED -> PaymentStatus.ACCEPTED;
      case REVIEW -> PaymentStatus.REVIEW;
      case BLOCKED -> PaymentStatus.REJECTED;
    };
  }
}

