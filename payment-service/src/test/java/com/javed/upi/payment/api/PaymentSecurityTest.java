package com.javed.upi.payment.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.javed.upi.payment.security.PaymentSecurityConfiguration;
import com.javed.upi.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("secure")
@WebMvcTest(PaymentController.class)
@Import(PaymentSecurityConfiguration.class)
@TestPropertySource(properties =
    "app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class PaymentSecurityTest {
  private static final String VALID_PAYMENT_REQUEST = """
      {
        "payerVpa": "alice@upi",
        "payeeVpa": "merchant@upi",
        "amount": 249.50,
        "currency": "INR",
        "channel": "MOBILE"
      }
      """;

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private PaymentService paymentService;

  @Test
  void paymentSubmissionRejectsUnauthenticatedRequestsInSecureProfile() throws Exception {
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_REQUEST))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/payments")
            .contentType(MediaType.TEXT_PLAIN)
            .content("{}"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(paymentService);
  }

  @Test
  void paymentLookupRemainsPublicInSecureProfile() throws Exception {
    mockMvc.perform(get("/api/payments/pay_123"))
        .andExpect(status().isOk());
  }

  @Test
  void paymentSubmissionRejectsJwtWithoutPaymentWriteScope() throws Exception {
    mockMvc.perform(post("/api/payments")
            .with(jwt().jwt(token -> token.claim("scope", "payment.read")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_REQUEST))
        .andExpect(status().isForbidden());
  }

  @Test
  void paymentSubmissionAcceptsJwtWithPaymentWriteScope() throws Exception {
    mockMvc.perform(post("/api/payments")
            .with(jwt().jwt(token -> token.claim("scope", "payment.write")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_REQUEST))
        .andExpect(status().isAccepted());
  }

  @Test
  void authenticatedUnsupportedMediaTypeReturnsStructuredError() throws Exception {
    mockMvc.perform(post("/api/payments")
            .with(jwt().jwt(token -> token.claim("scope", "payment.write")))
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
}
