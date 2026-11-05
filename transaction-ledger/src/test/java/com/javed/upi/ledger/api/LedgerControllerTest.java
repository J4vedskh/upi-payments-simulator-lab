package com.javed.upi.ledger.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.javed.upi.ledger.service.TransactionLedgerService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private TransactionLedgerService ledgerService;

  @Test
  void invalidPaymentEventsReturnBadRequestWithoutCallingTheLedger() throws Exception {
    for (String body : List.of("{}", "{\"paymentId\":", "")) {
      mockMvc.perform(post("/internal/events/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.timestamp").exists())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.message")
              .value("Request body is malformed or missing a required field."))
          .andExpect(jsonPath("$.path").value("/internal/events/payments"))
          .andExpect(jsonPath("$.validationErrors").isEmpty());
    }

    verifyNoInteractions(ledgerService);
  }

  @Test
  void unknownEventFieldsReturnStructuredErrorWithoutCallingTheLedger() throws Exception {
    mockMvc.perform(post("/internal/events/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "paymentId": "pay_123",
                  "payerVpa": "alice@upi",
                  "payeeVpa": "merchant@upi",
                  "amount": 249.50,
                  "currency": "INR",
                  "status": "ACCEPTED",
                  "decisionCode": "APPROVED",
                  "decisionMessage": "Payment approved.",
                  "decidedAt": "2026-11-05T05:00:00Z",
                  "unexpected": "not-declared"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message")
            .value("Request body is malformed or missing a required field."))
        .andExpect(jsonPath("$.path").value("/internal/events/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());

    verifyNoInteractions(ledgerService);
  }

  @Test
  void unsupportedEventMediaTypeReturnsStructuredErrorWithoutCallingTheLedger() throws Exception {
    mockMvc.perform(post("/internal/events/payments")
            .contentType(MediaType.TEXT_PLAIN)
            .content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.message")
            .value("Content-Type is not supported for this endpoint."))
        .andExpect(jsonPath("$.path").value("/internal/events/payments"))
        .andExpect(jsonPath("$.validationErrors").isEmpty());

    verifyNoInteractions(ledgerService);
  }

  @Test
  void missingLedgerEntryReturnsStructuredNotFound() throws Exception {
    when(ledgerService.find("pay_missing"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger entry not found: pay_missing"));

    mockMvc.perform(get("/api/ledger/pay_missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Ledger entry not found: pay_missing"))
        .andExpect(jsonPath("$.path").value("/api/ledger/pay_missing"));
  }
}

