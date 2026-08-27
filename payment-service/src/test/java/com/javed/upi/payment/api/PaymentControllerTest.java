package com.javed.upi.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.payment.security.PaymentSecurityConfiguration;
import com.javed.upi.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(PaymentController.class)
@Import(PaymentSecurityConfiguration.class)
class PaymentControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private PaymentService paymentService;

  @Test
  void submitPaymentReturnsAcceptedDecision() throws Exception {
    PaymentResponse response = new PaymentResponse(
        "pay_123",
        "alice@upi",
        "merchant@upi",
        new BigDecimal("249.50"),
        "INR",
        PaymentStatus.ACCEPTED,
        "APPROVED",
        "Payment passed all configured fraud checks.",
        Instant.parse("2026-05-27T10:00:00Z"));

    when(paymentService.submit(any(PaymentRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new PaymentRequest(
                "alice@upi",
                "merchant@upi",
                new BigDecimal("249.50"),
                "INR",
                "MOBILE"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.paymentId").value("pay_123"))
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.decisionCode").value("APPROVED"));

    verify(paymentService).submit(any(PaymentRequest.class));
  }

  @Test
  void invalidPaymentReturnsStructuredValidationErrors() throws Exception {
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "payerVpa": "",
                  "payeeVpa": "merchant@upi",
                  "amount": 0,
                  "currency": "",
                  "channel": "MOBILE"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors.payerVpa").exists())
        .andExpect(jsonPath("$.validationErrors.amount").exists())
        .andExpect(jsonPath("$.validationErrors.currency").exists());

    verifyNoInteractions(paymentService);
  }

  @Test
  void missingPaymentReturnsStructuredNotFound() throws Exception {
    when(paymentService.find("pay_missing"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: pay_missing"));

    mockMvc.perform(get("/api/payments/pay_missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Payment not found: pay_missing"))
        .andExpect(jsonPath("$.path").value("/api/payments/pay_missing"));
  }
}

