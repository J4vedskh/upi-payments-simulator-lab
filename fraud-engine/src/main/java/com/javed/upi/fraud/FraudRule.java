package com.javed.upi.fraud;

import com.javed.upi.events.PaymentCommand;
import java.util.Optional;

@FunctionalInterface
public interface FraudRule {
  Optional<FraudFinding> evaluate(PaymentCommand command);
}

