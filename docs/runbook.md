# Runbook

## Build

```bash
mvn -T 1C test
```

## Run Without Kafka

Use this for fast local demos and unit-test-friendly behavior:

```bash
mvn -pl payment-service spring-boot:run
mvn -pl transaction-ledger spring-boot:run
```

## Run With Kafka Profile

Start Kafka:

```bash
docker compose up -d kafka
```

Local Spring Boot runs use `localhost:29092`; Docker Compose services use `kafka:9092`.

Run both services with the Kafka profile:

```bash
mvn -pl payment-service spring-boot:run -Dspring-boot.run.profiles=kafka
mvn -pl transaction-ledger spring-boot:run -Dspring-boot.run.profiles=kafka
```

## Build Docs

```bash
pip install -r docs/requirements.txt
mkdocs build --strict
```

## Useful Endpoints

| Endpoint | Description |
| --- | --- |
| `GET /actuator/health` | Service health |
| `POST /api/payments` | Submit payment command |
| `GET /api/payments/{paymentId}` | Inspect payment decision |
| `GET /api/ledger` | List ledger entries |
| `GET /api/ledger/{paymentId}` | Inspect ledger entry |
