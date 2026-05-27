package com.javed.upi.ledger.api;

import com.javed.upi.events.PaymentEvent;
import com.javed.upi.ledger.service.LedgerEntry;
import com.javed.upi.ledger.service.TransactionLedgerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class LedgerController {
  private final TransactionLedgerService ledgerService;

  public LedgerController(TransactionLedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @PostMapping("/internal/events/payments")
  public LedgerEntry record(@RequestBody PaymentEvent event) {
    return ledgerService.record(event);
  }

  @GetMapping("/api/ledger")
  public List<LedgerEntry> all() {
    return ledgerService.all();
  }

  @GetMapping("/api/ledger/{paymentId}")
  public LedgerEntry find(@PathVariable String paymentId) {
    return ledgerService.find(paymentId);
  }
}

