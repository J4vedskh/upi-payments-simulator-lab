package com.javed.upi.ledger.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
class PrometheusEndpointIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void exposesBoundedLedgerRecordMetrics() throws Exception {
    mockMvc.perform(post("/internal/events/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "paymentId": "pay_metrics_test",
                  "payerVpa": "alice@upi",
                  "payeeVpa": "merchant@upi",
                  "amount": 50000.00,
                  "currency": "INR",
                  "status": "REVIEW",
                  "decisionCode": "HIGH_VALUE_PAYMENT",
                  "decisionMessage": "Payment amount requires manual review.",
                  "decidedAt": "2026-10-22T05:30:00Z"
                }
                """))
        .andExpect(status().isOk());

    String prometheus = mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    var recordSamples = prometheus.lines()
        .filter(line -> line.startsWith("upi_ledger_records_total{"))
        .toList();
    assertThat(recordSamples)
        .allMatch(line -> line.matches(
            "upi_ledger_records_total\\{status=\\\"(accepted|review|rejected)\\\"} [0-9.E+-]+"))
        .anyMatch(line -> line.contains("status=\"review\"") && line.endsWith(" 1.0"));
    assertThat(prometheus).doesNotContain(
        "pay_metrics_test", "alice@upi", "merchant@upi", "50000.0");
  }
}
