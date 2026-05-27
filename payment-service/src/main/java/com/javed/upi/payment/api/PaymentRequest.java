package com.javed.upi.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
    @NotBlank String payerVpa,
    @NotBlank String payeeVpa,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String currency,
    @NotBlank String channel) {
}

