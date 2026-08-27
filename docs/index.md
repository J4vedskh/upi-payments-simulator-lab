# UPI Payments Simulator Lab

This project is a recruiter-friendly backend and platform lab for a UPI-style payment system. It starts with a working Spring Boot payment API, rule-based fraud decisions, and a transaction ledger, then leaves a clear runway for Kafka, persistence, security, dashboards, and deployment hardening.

<div class="hero-grid" markdown>
<div class="hero-card" markdown>
**Payment Command API**

Accepts payer/payee VPAs, amount, currency, and channel, then returns a traceable payment decision.
</div>
<div class="hero-card" markdown>
**Fraud Rules Engine**

Blocks self-transfers and restricted payees, reviews high-value and non-INR payments.
</div>
<div class="hero-card" markdown>
**Event-Driven Ledger**

Records payment decisions through a local endpoint or Kafka consumer profile.
</div>
</div>

## Current Capabilities

| Capability | Status |
| --- | --- |
| Payment command submission | <span class="status-pill status-accepted">Ready</span> |
| Fraud rules and tests | <span class="status-pill status-accepted">Ready</span> |
| Transaction ledger service | <span class="status-pill status-accepted">Ready</span> |
| Kafka runtime profile | <span class="status-pill status-review">Starter</span> |
| Durable PostgreSQL persistence | <span class="status-pill status-review">Roadmap</span> |
| JWT-secured payment submission profile | <span class="status-pill status-accepted">Ready (opt-in)</span> |

## Why This Project Matters

The lab demonstrates backend engineering skills that matter in fintech and banking systems:

- clear service boundaries
- command/event data contracts
- fraud decisioning rules
- auditable transaction outcomes
- testable local behavior
- deployment and documentation discipline

