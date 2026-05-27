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
| `payment-service` | REST API for payment submission and fraud decisions |
| `transaction-ledger` | Records events and exposes payment ledger lookups |

## Fraud Decision Flow

```mermaid
sequenceDiagram
  participant C as Client
  participant P as Payment Service
  participant F as Fraud Engine
  participant K as Kafka/Event Publisher
  participant L as Ledger

  C->>P: POST /api/payments
  P->>F: evaluate PaymentCommand
  F-->>P: APPROVED, REVIEW, or BLOCKED
  P->>K: publish PaymentEvent
  K-->>L: consume PaymentEvent
  C-->>P: paymentId and decision
```

## Profiles

| Profile | Behavior |
| --- | --- |
| Default | Uses in-memory publishing so tests and demos do not require Kafka |
| `kafka` | Publishes and consumes `PaymentEvent` messages through Kafka |

