# Architecture

The first release keeps local demos simple while still modeling the production shape: a command API evaluates fraud rules, emits a payment event, and a ledger service records the decision.

```mermaid
flowchart LR
  Client["Client or test harness"] --> PaymentAPI["Payment Service REST API"]
  PaymentAPI --> Fraud["Fraud Rules Engine"]
  Fraud --> Decision{"Decision"}
  Decision -->|"APPROVED"| Accepted["PaymentEvent ACCEPTED"]
  Decision -->|"REVIEW"| Review["PaymentEvent REVIEW"]
  Decision -->|"BLOCKED"| Rejected["PaymentEvent REJECTED"]
  Accepted --> Kafka["Kafka topic: payment-events"]
  Review --> Kafka
  Rejected --> Kafka
  Kafka --> Ledger["Transaction Ledger Service"]
  Ledger --> Audit["Ledger query API"]
```

## Service Responsibilities

| Component | Responsibility |
| --- | --- |
| `shared-events` | Versioned command and event records shared by services |
| `fraud-engine` | Pure Java fraud rules with fast unit tests |
| `payment-service` | REST API with optional retry idempotency and fraud decisions |
| `transaction-ledger` | Retains the first successfully recorded event per payment ID and exposes ledger lookups |

The ledger handles at-least-once delivery by treating `paymentId` as its idempotency
key. Kafka and direct HTTP replays retain the successfully recorded immutable entry
and its recording time.

## Fraud Decision Flow

```mermaid
sequenceDiagram
  participant C as Client
  participant P as Payment Service
  participant F as Fraud Engine
  participant K as Kafka/Event Publisher
  participant L as Ledger

  C->>P: POST /api/payments (optional Idempotency-Key)
  P->>P: hash key and normalized request fingerprint
  P->>F: evaluate PaymentCommand
  F-->>P: APPROVED, REVIEW, or BLOCKED
  P->>K: publish PaymentEvent
  K-->>L: consume PaymentEvent
  C-->>P: paymentId and decision
```

For a key's first successful submission, the payment service stores only the
SHA-256 key and request-fingerprint hashes with the response. Equivalent retries
return that response before fraud evaluation or event publication; conflicting key
reuse returns a fixed `409`. This protection is local to one process until the
PostgreSQL persistence milestone.

The replay cache is bounded and expires entries from their initial successful
write; cache hits do not extend retention. Capacity eviction, expiry, or process
restart makes a later use of that key a new submission.

The payment service invokes its publisher before committing the local lookup entry
and decision metric. An immediate publisher exception returns a fixed `503` and
leaves no local idempotency success, allowing a keyed retry. Kafka acknowledgement is
asynchronous, so this boundary is not a durable-delivery or non-delivery guarantee;
that still requires an outbox.

## Profiles

| Profile | Behavior |
| --- | --- |
| Default | Uses in-memory publishing so tests and demos do not require Kafka |
| `kafka` | Publishes and consumes `PaymentEvent` messages through Kafka |
| `secure` | Requires an HS256 JWT with `payment.write` for payment submission while keeping payment reads and health/info public |

## Security Boundary

Security is deliberately opt-in so the original local and test workflow does not
change. In the `secure` profile, the payment service runs as a stateless OAuth2
resource server. It validates JWT signatures with a Base64 key provided through the
runtime environment, maps the standard `scope` claim to Spring Security authorities,
and checks `SCOPE_payment.write` before the controller handles `POST /api/payments`.

This symmetric-key decoder is a self-contained lab profile, not an identity-provider
integration. A production deployment should use managed key distribution and add
issuer and audience validation.

