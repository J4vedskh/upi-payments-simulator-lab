package com.javed.upi.fraud;

public record FraudFinding(PaymentRisk risk, String code, String message) {
}

