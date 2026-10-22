package com.javed.upi.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("secure")
@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
@TestPropertySource(properties =
    "app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class PrometheusEndpointIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void exposesBoundedPaymentDecisionMetricsWithoutScrapeCredentialsInSecureProfile()
      throws Exception {
    mockMvc.perform(post("/api/payments")
            .with(jwt().jwt(token -> token.claim("scope", "payment.write")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "payerVpa": "alice@upi",
                  "payeeVpa": "merchant@upi",
                  "amount": 249.50,
                  "currency": "INR",
                  "channel": "MOBILE"
                }
                """))
        .andExpect(status().isAccepted());

    String prometheus = mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    var decisionSamples = prometheus.lines()
        .filter(line -> line.startsWith("upi_payment_decisions_total{"))
        .toList();
    assertThat(decisionSamples)
        .allMatch(line -> line.matches(
            "upi_payment_decisions_total\\{status=\\\"(accepted|review|rejected)\\\"} [0-9.E+-]+"))
        .anyMatch(line -> line.contains("status=\"accepted\"") && line.endsWith(" 1.0"));
    assertThat(prometheus).doesNotContain("alice@upi", "merchant@upi", "249.5");
  }
}
