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

## Configure Fraud Thresholds

The payment service uses these defaults:

| Setting | Environment variable | Default | Boundary behavior |
| --- | --- | ---: | --- |
| Review amount | `UPI_FRAUD_REVIEW_THRESHOLD` | `50000.00` | Amounts greater than or equal to this value enter review. |
| Block amount | `UPI_FRAUD_BLOCK_THRESHOLD` | `200000.00` | Amounts greater than or equal to this value are rejected. |

Both values must be positive, and review must remain lower than block. The
service fails startup if that policy is invalid. Set the values before starting
the payment service, for example:

```powershell
$env:UPI_FRAUD_REVIEW_THRESHOLD = "25000.00"
$env:UPI_FRAUD_BLOCK_THRESHOLD = "100000.00"
mvn -pl payment-service spring-boot:run
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

## Run With JWT Security

The default profile remains permit-all. Secure mode protects only payment submission;
payment lookup, health, and info endpoints remain public.

Set a Base64-encoded HS256 key of at least 32 decoded bytes, then start the payment
service with the `secure` profile:

```powershell
$jwtKeyBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtKeyBytes)
$env:PAYMENT_JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
mvn -pl payment-service spring-boot:run "-Dspring-boot.run.profiles=secure"
```

Clients must send a bearer JWT containing `payment.write` in its `scope` claim.
Missing or invalid tokens return `401`; authenticated tokens without the scope return
`403`. Use `secure,kafka` when both profiles are required.

## Prometheus Metrics

After starting the services, inspect their Prometheus endpoints:

```bash
curl http://localhost:8081/actuator/prometheus
curl http://localhost:8082/actuator/prometheus
```

| Metric | Meaning |
| --- | --- |
| `upi_payment_decisions_total{status="..."}` | Payment decisions evaluated by the fraud engine |
| `upi_ledger_records_total{status="..."}` | Ledger record operations, including replayed events that overwrite an existing in-memory entry |

The only custom label is `status`, with the fixed values `accepted`, `review`,
and `rejected`. Payment IDs, VPAs, amounts, currencies, channels, decision codes,
and request identifiers are deliberately excluded from labels.

Each service's counters reset when that service restarts. An external Prometheus
server can retain historical samples across restarts. The ledger counter measures
record operations, not the number of unique entries currently held in memory.

## Build Docs

```bash
pip install -r docs/requirements.txt
mkdocs build --strict
```

## Useful Endpoints

| Endpoint | Description |
| --- | --- |
| `GET /actuator/health` | Service health |
| `GET /actuator/prometheus` | Prometheus metrics; public in the default and secure profiles |
| `POST /api/payments` | Submit payment command; requires `payment.write` in secure mode |
| `GET /api/payments/{paymentId}` | Inspect payment decision |
| `GET /api/ledger` | List ledger entries |
| `GET /api/ledger/{paymentId}` | Inspect ledger entry |
