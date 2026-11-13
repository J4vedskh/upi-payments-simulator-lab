package com.javed.upi.payment.service;

public final class PaymentEventPublicationException extends RuntimeException {
  public PaymentEventPublicationException(Throwable cause) {
    super("Payment event publication is temporarily unavailable.", cause);
  }
}
