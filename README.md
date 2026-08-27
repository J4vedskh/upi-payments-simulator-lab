# UPI Payments Simulator Lab

UPI Payments Simulator Lab is a portfolio-grade Java project that models a UPI-style payment flow with fraud rules, Kafka event publishing, a transaction ledger, Docker, CI, Kubernetes starter manifests, and live documentation.

Live docs: https://j4vedskh.github.io/upi-payments-simulator-lab/

Project deep dive: [PROJECT_EXPLANATION.md](PROJECT_EXPLANATION.md)

## What It Shows

| Area | Implementation |
| --- | --- |
| Backend | Java 17, Spring Boot REST APIs, validation, Actuator |
| Security | Default permit-all behavior plus an opt-in JWT resource-server profile for payment writes |
| Eventing | Kafka profile for payment events, local in-memory publisher for tests |
| Fraud logic | Rule-based approval, review, and rejection decisions |
| Ledger | Transaction ledger service that records payment events |
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
  -H "Content-Type: application/json" \
  -d "{\"payerVpa\":\"alice@upi\",\"payeeVpa\":\"merchant@upi\",\"amount\":249.50,\"currency\":\"INR\",\"channel\":\"MOBILE\"}"
```

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

The docs use Material for MkDocs, Mermaid diagrams, and a versioned OpenAPI contract.
