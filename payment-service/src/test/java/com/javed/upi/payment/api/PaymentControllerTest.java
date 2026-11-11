package com.javed.upi.payment.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javed.upi.events.PaymentStatus;
import com.javed.upi.payment.security.PaymentSecurityConfiguration;
import com.javed.upi.payment.service.PaymentIdempotencyException;
import com.javed.upi.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    when(paymentService.submit(any(PaymentRequest.class), isNull())).thenReturn(response);

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

    verify(paymentService).submit(any(PaymentRequest.class), isNull());
  }

  @Test
  void idempotencyKeyIsForwardedToThePaymentService() throws Exception {
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
    when(paymentService.submit(any(PaymentRequest.class), eq("retry-key-123")))
        .thenReturn(response);

    mockMvc.perform(post("/api/payments")
            .header("Idempotency-Key", "retry-key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new PaymentRequest(
                "alice@upi",
                "merchant@upi",
                new BigDecimal("249.50"),
                "INR",
                "MOBILE"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.paymentId").value("pay_123"));

    verify(paymentService).submit(any(PaymentRequest.class), eq("retry-key-123"));
  }

  @Test
  void conflictingIdempotencyKeyReturnsStableConflictWithoutEchoingTheKey() throws Exception {
    when(paymentService.submit(any(PaymentRequest.class), eq("private-retry-key")))
        .thenThrow(PaymentIdempotencyException.conflict());

    mockMvc.perform(post("/api/payments")
            .header("Idempotency-Key", "private-retry-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new PaymentRequest(
                "alice@upi",
                "merchant@upi",
                new BigDecimal("250.00"),
                "INR",
                "MOBILE"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"))
        .andExpect(jsonPath("$.message")
            .value("Idempotency-Key was already used for a different payment request."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty())
        .andExpect(content().string(not(containsString("private-retry-key"))));
  }

  @Test
  void invalidIdempotencyKeyReturnsStableValidationError() throws Exception {
    when(paymentService.submit(any(PaymentRequest.class), eq("bad key")))
        .thenThrow(PaymentIdempotencyException.invalidKey());

    mockMvc.perform(post("/api/payments")
            .header("Idempotency-Key", "bad key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new PaymentRequest(
                "alice@upi",
                "merchant@upi",
                new BigDecimal("249.50"),
                "INR",
                "MOBILE"))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message")
            .value("Idempotency-Key must contain 1 to 128 visible ASCII characters."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());
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
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Request validation failed."))
        .andExpect(jsonPath("$.validationErrors.payerVpa").exists())
        .andExpect(jsonPath("$.validationErrors.payeeVpa").exists())
        .andExpect(jsonPath("$.validationErrors.amount").exists())
        .andExpect(jsonPath("$.validationErrors.currency").exists())
        .andExpect(jsonPath("$.validationErrors.channel").exists());

    verifyNoInteractions(paymentService);
  }

  @Test
  void whitespaceOnlyPaymentFieldsReturnStructuredValidationErrors() throws Exception {
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "payerVpa": "   ",
                  "payeeVpa": "   ",
                  "amount": 249.50,
                  "currency": "   ",
                  "channel": "   "
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request validation failed."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors.length()").value(4))
        .andExpect(jsonPath("$.validationErrors.payerVpa").exists())
        .andExpect(jsonPath("$.validationErrors.payeeVpa").exists())
        .andExpect(jsonPath("$.validationErrors.currency").exists())
        .andExpect(jsonPath("$.validationErrors.channel").exists());

    verifyNoInteractions(paymentService);
  }

  @Test
  void unreadablePaymentBodiesReturnStructuredErrorWithoutCallingService() throws Exception {
    for (String body : List.of("{\"payerVpa\":", "", "[]")) {
      mockMvc.perform(post("/api/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.timestamp").exists())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.message").value("Request body is malformed."))
          .andExpect(jsonPath("$.path").value("/api/payments"))
          .andExpect(jsonPath("$.validationErrors").isEmpty());
    }

    verifyNoInteractions(paymentService);
  }

  @Test
  void unknownPaymentFieldsReturnStructuredErrorWithoutCallingService() throws Exception {
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "payerVpa": "alice@upi",
                  "payeeVpa": "merchant@upi",
                  "amount": 249.50,
                  "currency": "INR",
                  "channel": "MOBILE",
                  "unexpected": "not-declared"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request body is malformed."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());

    verifyNoInteractions(paymentService);
  }

  @Test
  void unsupportedPaymentMediaTypeReturnsStructuredErrorWithoutCallingService() throws Exception {
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.TEXT_PLAIN)
            .content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.message")
            .value("Content-Type is not supported for this endpoint."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());

    verifyNoInteractions(paymentService);
  }

  @Test
  void missingPaymentReturnsStructuredNotFound() throws Exception {
    when(paymentService.find("pay_missing"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found."));

    mockMvc.perform(get("/api/payments/pay_missing"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Payment not found."))
        .andExpect(jsonPath("$.path").value("/api/payments/pay_missing"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());
  }
}

