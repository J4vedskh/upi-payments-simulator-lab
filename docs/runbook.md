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

## Build Docs

```bash
pip install -r docs/requirements.txt
mkdocs build --strict
```

## Useful Endpoints

| Endpoint | Description |
| --- | --- |
| `GET /actuator/health` | Service health |
| `POST /api/payments` | Submit payment command; requires `payment.write` in secure mode |
| `GET /api/payments/{paymentId}` | Inspect payment decision |
| `GET /api/ledger` | List ledger entries |
| `GET /api/ledger/{paymentId}` | Inspect ledger entry |
