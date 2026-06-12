# UPI Payments Simulator Lab

UPI Payments Simulator Lab is a portfolio-grade Java project that models a UPI-style payment flow with fraud rules, Kafka event publishing, a transaction ledger, Docker, CI, Kubernetes starter manifests, and live documentation.

Live docs: https://j4vedskh.github.io/upi-payments-simulator-lab/

Project deep dive: [PROJECT_EXPLANATION.md](PROJECT_EXPLANATION.md)

## What It Shows

| Area | Implementation |
| --- | --- |
| Backend | Java 17, Spring Boot REST APIs, validation, Actuator |
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
