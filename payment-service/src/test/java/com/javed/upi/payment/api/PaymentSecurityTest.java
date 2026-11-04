package com.javed.upi.payment.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.javed.upi.payment.security.PaymentSecurityConfiguration;
import com.javed.upi.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
    assertStructuredUnauthorized(post("/api/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_PAYMENT_REQUEST));
    assertStructuredUnauthorized(post("/api/payments")
        .contentType(MediaType.TEXT_PLAIN)
        .content("{}"));
    assertStructuredUnauthorized(post("/api/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"payerVpa\":"));
    assertStructuredUnauthorized(post("/api/payments")
        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_PAYMENT_REQUEST));

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
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Access is denied."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());

    verifyNoInteractions(paymentService);
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

  @Test
  void authenticatedUnreadableBodyReturnsStructuredError() throws Exception {
    mockMvc.perform(post("/api/payments")
            .with(jwt().jwt(token -> token.claim("scope", "payment.write")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"payerVpa\":"))
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

  private void assertStructuredUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
    mockMvc.perform(request)
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, not(containsString("invalid-token"))))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("Bearer authentication is required."))
        .andExpect(jsonPath("$.path").value("/api/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());
  }
}
