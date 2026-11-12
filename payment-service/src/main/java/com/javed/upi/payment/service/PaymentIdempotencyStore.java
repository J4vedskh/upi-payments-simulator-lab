package com.javed.upi.payment.service;

import com.javed.upi.payment.api.PaymentResponse;
import com.javed.upi.payment.config.PaymentIdempotencyProperties;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class PaymentIdempotencyStore {
  private final long maxEntries;
  private final long retentionNanos;
  private final LongSupplier ticker;
  private final ConcurrentMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
  private final ConcurrentSkipListMap<RetentionOrder, RetentionIndex> insertionOrder =
      new ConcurrentSkipListMap<>();
  private final AtomicLong sequence = new AtomicLong();

  public PaymentIdempotencyStore(PaymentIdempotencyProperties properties) {
    this(properties, System::nanoTime);
  }

  PaymentIdempotencyStore(PaymentIdempotencyProperties properties, LongSupplier ticker) {
    Objects.requireNonNull(properties, "properties are required");
    if (properties.maxEntries() < 1) {
      throw new IllegalArgumentException("maxEntries must be greater than or equal to 1");
    }
    if (properties.retention() == null
        || properties.retention().isZero()
        || properties.retention().isNegative()) {
      throw new IllegalArgumentException("retention must be greater than zero");
    }
    this.maxEntries = properties.maxEntries();
    this.retentionNanos = properties.retention().toNanos();
    this.ticker = Objects.requireNonNull(ticker, "ticker is required");
  }

  PaymentResponse execute(
      String keyHash,
      String requestFingerprint,
      Supplier<PaymentResponse> newPayment) {
    long now = ticker.getAsLong();
    AtomicReference<IdempotencyRecord> created = new AtomicReference<>();
    IdempotencyRecord record = records.compute(keyHash, (ignored, existing) -> {
      if (existing == null || isExpired(existing, now)) {
        IdempotencyRecord replacement = new IdempotencyRecord(
            requestFingerprint,
            newPayment.get(),
            ticker.getAsLong());
        created.set(replacement);
        return replacement;
      }
      if (!existing.requestFingerprint().equals(requestFingerprint)) {
        throw PaymentIdempotencyException.conflict();
      }
      return existing;
    });

    IdempotencyRecord createdRecord = created.get();
    maintain(keyHash, createdRecord, ticker.getAsLong());
    return record.response();
  }

  synchronized void cleanUp() {
    maintain(null, null, ticker.getAsLong());
  }

  long estimatedSize() {
    cleanUp();
    return records.size();
  }

  boolean contains(String keyHash) {
    cleanUp();
    return records.containsKey(keyHash);
  }

  private synchronized void maintain(
      String keyHash,
      IdempotencyRecord createdRecord,
      long now) {
    if (createdRecord != null) {
      insertionOrder.put(
          new RetentionOrder(createdRecord.createdAtNanos(), sequence.incrementAndGet()),
          new RetentionIndex(keyHash, createdRecord));
    }

    while (true) {
      Map.Entry<RetentionOrder, RetentionIndex> oldest = insertionOrder.firstEntry();
      if (oldest == null || !isExpired(oldest.getValue().record(), now)) {
        break;
      }
      insertionOrder.pollFirstEntry();
      records.remove(oldest.getValue().keyHash(), oldest.getValue().record());
    }

    while (records.size() > maxEntries) {
      Map.Entry<RetentionOrder, RetentionIndex> oldest = insertionOrder.pollFirstEntry();
      if (oldest == null) {
        break;
      }
      records.remove(oldest.getValue().keyHash(), oldest.getValue().record());
    }
  }

  private boolean isExpired(IdempotencyRecord record, long now) {
    return now - record.createdAtNanos() >= retentionNanos;
  }

  private record IdempotencyRecord(
      String requestFingerprint,
      PaymentResponse response,
      long createdAtNanos) {
  }

  private record RetentionIndex(String keyHash, IdempotencyRecord record) {
  }

  private record RetentionOrder(long createdAtNanos, long sequence)
      implements Comparable<RetentionOrder> {
    @Override
    public int compareTo(RetentionOrder other) {
      int timestampComparison = Long.compare(createdAtNanos, other.createdAtNanos);
      return timestampComparison != 0
          ? timestampComparison
          : Long.compare(sequence, other.sequence);
    }
  }
}
