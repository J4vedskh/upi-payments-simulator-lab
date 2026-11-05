# API Reference

The versioned OpenAPI contract lives at [`docs/api/openapi.yaml`](api/openapi.yaml).

## Authentication Profiles

The default profile is permit-all so existing local demos and tests work without a
token. With the `secure` profile active, `POST /api/payments` requires an HS256 bearer
JWT whose `scope` claim contains `payment.write`.

| Request | Default profile | `secure` profile |
| --- | --- | --- |
| `POST /api/payments` | Public | JWT with `payment.write` |
| `GET /api/payments/{paymentId}` | Public | Public |
| `GET /actuator/health` and `GET /actuator/info` | Public | Public |
| `GET /actuator/prometheus` | Public | Public |

Secure payment submission returns `401` with error `UNAUTHORIZED` and fixed message
`Bearer authentication is required.` when the token is missing or invalid. An
authenticated token without `payment.write` returns `403` with error `FORBIDDEN`
and fixed message `Access is denied.` Both responses use the standard six-field
JSON error shape, keep `validationErrors` empty, and preserve the standard
`WWW-Authenticate` header. Their fixed JSON bodies do not reflect token values or
decoder details; the challenge header can still include standard Bearer protocol
error metadata. The signing key is supplied at runtime through
`PAYMENT_JWT_SECRET`; no signing credential is stored in Git.

## Submit Payment

```http
POST /api/payments
Content-Type: application/json
Authorization: Bearer <signed-jwt>  # required only with the secure profile

{
  "payerVpa": "alice@upi",
  "payeeVpa": "merchant@upi",
  "amount": 249.50,
  "currency": "INR",
  "channel": "MOBILE"
}
```

Example response:

```json
{
  "paymentId": "pay_7b873df0-3928-4d55-b015-bef1bd8cb1bd",
  "payerVpa": "alice@upi",
  "payeeVpa": "merchant@upi",
  "amount": 249.50,
  "currency": "INR",
  "status": "ACCEPTED",
  "decisionCode": "APPROVED",
  "decisionMessage": "Payment passed all configured fraud checks.",
  "decidedAt": "2026-05-27T10:00:00Z"
}
```

## Decision Codes

| Code | Status | Meaning |
| --- | --- | --- |
| `APPROVED` | `ACCEPTED` | Payment passed all configured rules |
| `SELF_TRANSFER` | `REJECTED` | Payer and payee VPA are the same |
| `RESTRICTED_PAYEE` | `REJECTED` | Payee matched a restricted pattern |
| `LIMIT_EXCEEDED` | `REJECTED` | Amount reached or exceeded the hard limit |
| `HIGH_VALUE_PAYMENT` | `REVIEW` | Amount requires manual review |
| `NON_INR_PAYMENT` | `REVIEW` | Currency requires review |

The review and block boundaries default to `50000.00` and `200000.00`.
Configure them with `UPI_FRAUD_REVIEW_THRESHOLD` and
`UPI_FRAUD_BLOCK_THRESHOLD`. Both must be positive, review must be lower than
block, and each boundary is inclusive.

## Ledger Lookup

```http
GET /api/ledger/{paymentId}
```

The ledger can also ingest a payment event through `POST /internal/events/payments`
for local demos without Kafka. Malformed JSON, an empty body, or a missing
required event field returns `400` with the same six-field error structure. The
fixed error is `VALIDATION_FAILED`, the message is `Request body is malformed or
missing a required field.`, and `validationErrors` is empty. Parser details and
submitted event values are not returned.

## Error Response Shape

Validation, lookup, and unsupported-media failures return a consistent JSON shape:

```json
{
  "timestamp": "2026-05-29T10:30:00Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/payments",
  "validationErrors": {
    "amount": "must be greater than or equal to 0.01",
    "payerVpa": "must not be blank"
  }
}
```

For payment submission, a bindable JSON object with invalid or missing fields,
including `{}`, returns `Request validation failed.` with field-level entries.
Malformed JSON, an empty body, or the wrong top-level JSON type returns `400` with
the same `VALIDATION_FAILED` code, fixed message `Request body is malformed.`, and
an empty `validationErrors` object. Parser details and submitted values are not
returned. In the secure profile, authentication and `payment.write` authorization
still happen before body parsing, so those failures remain `401` or `403`.

The payment and direct-ledger HTTP request schemas are closed: an undeclared JSON
field is rejected with the same safe structured `400` used for unreadable input,
and neither service method is called. This strict binding applies to HTTP request
bodies; Kafka deserialization remains a separate profile-level boundary.

Both `POST /api/payments` and `POST /internal/events/payments` use Spring's JSON
message conversion. An unsupported request content type returns `415` from ledger
intake. Payment submission does the same in the default profile or after
secure-profile authentication and `payment.write` authorization succeed; secure
authentication and authorization failures remain `401` or `403`. The structured
`415` uses error `UNSUPPORTED_MEDIA_TYPE`, message `Content-Type is not supported
for this endpoint.`, the request path, and an empty `validationErrors` object.
The rejected header value and body are not reflected in the response.

This makes client behavior predictable and keeps API failures easy to demonstrate in tests and docs.

## Prometheus Metrics

The services expose operational metrics at `GET /actuator/prometheus`. The
payment service publishes `upi_payment_decisions_total`, and the ledger publishes
`upi_ledger_records_total`. Each counter uses only the bounded `status` label;
request identifiers and payment details are excluded.
