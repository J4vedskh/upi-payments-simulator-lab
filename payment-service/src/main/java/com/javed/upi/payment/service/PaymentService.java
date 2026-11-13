package com.javed.upi.payment.service;

import com.javed.upi.events.PaymentCommand;
import com.javed.upi.events.PaymentEvent;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.fraud.FraudDecision;
import com.javed.upi.fraud.FraudEngine;
import com.javed.upi.fraud.PaymentRisk;
import com.javed.upi.payment.api.PaymentRequest;
import com.javed.upi.payment.api.PaymentResponse;
import com.javed.upi.payment.observability.PaymentDecisionMetrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

  private final FraudEngine fraudEngine;
  private final PaymentEventPublisher eventPublisher;
  private final PaymentDecisionMetrics decisionMetrics;
  private final Clock clock;
  private final PaymentIdempotencyStore idempotencyStore;
  private final Map<String, PaymentEvent> payments = new ConcurrentHashMap<>();

  @Autowired
  public PaymentService(
      FraudEngine fraudEngine,
      PaymentEventPublisher eventPublisher,
      PaymentDecisionMetrics decisionMetrics,
      PaymentIdempotencyStore idempotencyStore) {
    this(fraudEngine, eventPublisher, decisionMetrics, Clock.systemUTC(), idempotencyStore);
  }

  PaymentService(
      FraudEngine fraudEngine,
      PaymentEventPublisher eventPublisher,
      PaymentDecisionMetrics decisionMetrics,
      Clock clock,
      PaymentIdempotencyStore idempotencyStore) {
    this.fraudEngine = fraudEngine;
    this.eventPublisher = eventPublisher;
    this.decisionMetrics = decisionMetrics;
    this.clock = clock;
    this.idempotencyStore = idempotencyStore;
  }

  public PaymentResponse submit(PaymentRequest request, String idempotencyKey) {
    String validatedKey = validateIdempotencyKey(idempotencyKey);
    if (validatedKey == null) {
      return processNewPayment(request);
    }

    String keyHash = sha256(validatedKey);
    String requestFingerprint = requestFingerprint(request);
    return idempotencyStore.execute(
        keyHash,
        requestFingerprint,
        () -> processNewPayment(request));
  }

  private PaymentResponse processNewPayment(PaymentRequest request) {
    Instant now = Instant.now(clock);
    PaymentCommand command = new PaymentCommand(
        "pay_" + UUID.randomUUID(),
        request.payerVpa(),
        request.payeeVpa(),
        request.amount(),
        request.currency().toUpperCase(Locale.ROOT),
        request.channel().toUpperCase(Locale.ROOT),
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

    try {
      eventPublisher.publish(event);
    } catch (RuntimeException exception) {
      throw new PaymentEventPublicationException(exception);
    }
    payments.put(event.paymentId(), event);
    decisionMetrics.record(event.status());
    return PaymentResponse.from(event);
  }

  private static String validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null) {
      return null;
    }
    String normalized = idempotencyKey;
    boolean valid = !normalized.isEmpty()
        && normalized.length() <= MAX_IDEMPOTENCY_KEY_LENGTH
        && normalized.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    if (!valid) {
      throw PaymentIdempotencyException.invalidKey();
    }
    return normalized;
  }

  private static String requestFingerprint(PaymentRequest request) {
    StringBuilder canonical = new StringBuilder();
    appendCanonicalField(canonical, request.payerVpa());
    appendCanonicalField(canonical, request.payeeVpa());
    appendCanonicalField(canonical, request.amount().stripTrailingZeros().toPlainString());
    appendCanonicalField(canonical, request.currency().toUpperCase(Locale.ROOT));
    appendCanonicalField(canonical, request.channel().toUpperCase(Locale.ROOT));
    return sha256(canonical.toString());
  }

  private static void appendCanonicalField(StringBuilder canonical, String value) {
    canonical.append(value.length()).append(':').append(value);
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
    }
  }

  public PaymentResponse find(String paymentId) {
    PaymentEvent event = payments.get(paymentId);
    if (event == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found.");
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

