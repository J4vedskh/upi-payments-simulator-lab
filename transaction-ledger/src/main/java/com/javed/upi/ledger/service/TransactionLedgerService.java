package com.javed.upi.ledger.service;

import com.javed.upi.events.PaymentEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionLedgerService {
  private final Clock clock;
  private final Map<String, LedgerEntry> entries = new ConcurrentHashMap<>();

  public TransactionLedgerService() {
    this(Clock.systemUTC());
  }

  TransactionLedgerService(Clock clock) {
    this.clock = clock;
  }

  public LedgerEntry record(PaymentEvent event) {
    LedgerEntry entry = LedgerEntry.from(event, Instant.now(clock));
    entries.put(entry.paymentId(), entry);
    return entry;
  }

  public LedgerEntry find(String paymentId) {
    LedgerEntry entry = entries.get(paymentId);
    if (entry == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger entry not found: " + paymentId);
    }
    return entry;
  }

  public List<LedgerEntry> all() {
    return entries.values().stream()
        .sorted(Comparator.comparing(LedgerEntry::recordedAt).reversed())
        .toList();
  }
}

