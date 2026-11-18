# UPI Payments Simulator Lab

UPI Payments Simulator Lab is a portfolio-grade Java project that models a UPI-style payment flow with fraud rules, Kafka event publishing, a transaction ledger, Docker, CI, Kubernetes starter manifests, and live documentation.

Live docs: https://j4vedskh.github.io/upi-payments-simulator-lab/

Read-only API Explorer: https://j4vedskh.github.io/upi-payments-simulator-lab/api-explorer/

Project deep dive: [PROJECT_EXPLANATION.md](PROJECT_EXPLANATION.md)

## What It Shows

| Area | Implementation |
| --- | --- |
| Backend | Java 17, Spring Boot REST APIs, validation, Actuator |
| Security | Default permit-all behavior plus an opt-in JWT resource-server profile for payment writes |
| Eventing | Kafka profile for payment events, local in-memory publisher for tests |
| Fraud logic | Rule-based approval, review, and rejection decisions |
| Ledger | Idempotent transaction ledger that retains the first successfully recorded event per payment ID |
| Observability | Prometheus decision, publication-invocation, and ledger counters with bounded labels |
| Platform | Dockerfiles, Docker Compose, Kubernetes starter manifests |
| Delivery | GitHub Actions CI and GitHub Pages documentation |

## Services

| Service | Port | Responsibility |
| --- | ---: | --- |
| Payment Service | 8081 | Accept payment commands, run fraud checks, publish payment decisions |
| Transaction Ledger | 8082 | Consume payment events and expose ledger lookups |

## Quick Start

Build and test all modules with JDK 17:

```bash
mvn -T 1C test
```

Run the services locally without Kafka:

```bash
mvn -pl payment-service spring-boot:run
mvn -pl transaction-ledger spring-boot:run
```

Submit a payment:

```bash
curl -X POST http://localhost:8081/api/payments \
  -H "Idempotency-Key: demo-payment-001" \
  -H "Content-Type: application/json" \
  -d "{\"payerVpa\":\"alice@upi\",\"payeeVpa\":\"merchant@upi\",\"amount\":249.50,\"currency\":\"INR\",\"channel\":\"MOBILE\"}"
```

### Retry payment submission safely

`Idempotency-Key` is optional, so clients that omit it keep the existing
new-payment behavior. A key must contain 1 to 128 visible ASCII characters. After
an initial keyed submission returns `202`, an equivalent retry returns the original
payment decision without running fraud rules again, incrementing decision metrics,
or publishing another event. Reusing the key for different normalized payment
details returns the fixed `409 IDEMPOTENCY_CONFLICT` response.

The cache stores SHA-256 hashes of the key and normalized request fingerprint plus
the original payment response; it does not retain or echo the raw key. Replay state
is local to one running payment service and resets on restart, so cross-instance
durability remains part of the PostgreSQL roadmap. A synchronous publication
failure before the initial `202` is returned is not cached as a successful result.

If the event publisher throws while it is invoked, the API returns a fixed
`503 EVENT_PUBLICATION_FAILED` response before committing the local payment lookup,
decision metric, or idempotency result. A keyed retry can therefore try again. This
only covers a synchronous invocation failure: the Kafka adapter does not await broker
acknowledgement, and an adapter that throws after an external side effect may still
have delivered an event. Durable publication recovery remains an outbox concern.

By default, at most 10,000 successful keyed responses are retained for 24 hours
from their initial write. Replaying a key does not extend that period. Override the
limits with `UPI_IDEMPOTENCY_MAX_ENTRIES` and `UPI_IDEMPOTENCY_RETENTION` (for
example, `45m`). Once a key expires or is evicted for capacity, submitting it again
creates a new payment.

### Configure fraud thresholds

The amount review threshold defaults to `50000.00` and the hard block threshold
defaults to `200000.00`. Amounts at or above the review threshold require
review; amounts at or above the block threshold are rejected. Override both
before starting the payment service:

```powershell
$env:UPI_FRAUD_REVIEW_THRESHOLD = "25000.00"
$env:UPI_FRAUD_BLOCK_THRESHOLD = "100000.00"
mvn -pl payment-service spring-boot:run
```

Both values must be positive, and the review threshold must be lower than the
block threshold. Invalid settings stop application startup. Self-transfer,
restricted-payee, and non-INR rules continue to apply independently.

### Inspect business metrics

Both services expose Prometheus metrics without requiring credentials, including
when the payment service uses the `secure` profile:

```bash
curl http://localhost:8081/actuator/prometheus
curl http://localhost:8082/actuator/prometheus
```

`upi_payment_decisions_total` counts payment decisions by `status`, and
`upi_payment_publications_total` counts synchronous publisher invocations by the
fixed `outcome` values `success` and `failure`. A success means the invocation
returned; it does not prove Kafka acknowledgement or delivery. A failure means the
publisher threw immediately and drove the structured `503` path.

`upi_payment_publication_acknowledgements_total` counts how the Kafka send future
completes, again with only `success` and `failure`. These series are pre-registered
and remain zero outside the `kafka` profile. A successful acknowledgement is Kafka
client confirmation, not proof that the ledger consumed the event or that delivery
is durable/exactly-once.

`upi_ledger_records_total` counts first-time ledger entries by `status`. Replayed
payment IDs return the successfully recorded immutable entry without incrementing
the ledger counter. Custom labels are bounded: `status` uses `accepted`, `review`,
or `rejected`, while publication and acknowledgement `outcome` use `success` or
`failure`. Payment IDs, VPAs, amounts, broker details, and other request values are
not used as labels.

### Opt-in secure profile

The default profile remains permit-all for local demos and existing tests. To protect
`POST /api/payments`, activate the `secure` profile and provide a Base64-encoded key
that decodes to at least 32 bytes:

```powershell
$jwtKeyBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtKeyBytes)
$env:PAYMENT_JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
mvn -pl payment-service spring-boot:run "-Dspring-boot.run.profiles=secure"
```

Secure mode accepts HS256 bearer tokens whose `scope` claim contains
`payment.write`. Payment lookup and `/actuator/health` remain public:

```bash
curl -X POST http://localhost:8081/api/payments \
  -H "Authorization: Bearer <signed-jwt>" \
  -H "Content-Type: application/json" \
  -d "{\"payerVpa\":\"alice@upi\",\"payeeVpa\":\"merchant@upi\",\"amount\":249.50,\"currency\":\"INR\",\"channel\":\"MOBILE\"}"
```

The repository contains no JWT signing key. `PAYMENT_JWT_SECRET` must come from the
runtime environment and should be stored in a secret manager outside local demos.

Run infrastructure for the Kafka profile:

```bash
docker compose up -d kafka
```

The Kafka broker is exposed to local JVM processes on `localhost:29092` and to containers on `kafka:9092`.

## Documentation

Install and build the documentation site:

```bash
pip install -r docs/requirements.txt
mkdocs build --strict
```

The docs use Material for MkDocs, Mermaid diagrams, a versioned OpenAPI contract,
and a pinned, self-hosted, read-only RapiDoc explorer.
