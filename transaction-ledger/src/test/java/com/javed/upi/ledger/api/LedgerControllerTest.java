package com.javed.upi.ledger.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.javed.upi.ledger.service.TransactionLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private TransactionLedgerService ledgerService;

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

