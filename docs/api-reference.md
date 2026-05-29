# API Reference

The versioned OpenAPI contract lives at [`docs/api/openapi.yaml`](api/openapi.yaml).

## Submit Payment

```http
POST /api/payments
Content-Type: application/json

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
| `LIMIT_EXCEEDED` | `REJECTED` | Amount exceeded the hard limit |
| `HIGH_VALUE_PAYMENT` | `REVIEW` | Amount requires manual review |
| `NON_INR_PAYMENT` | `REVIEW` | Currency requires review |

## Ledger Lookup

```http
GET /api/ledger/{paymentId}
```

The ledger can also ingest a payment event through `POST /internal/events/payments` for local demos without Kafka.

## Error Response Shape

Validation and lookup failures return a consistent JSON shape:

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

This makes client behavior predictable and keeps API failures easy to demonstrate in tests and docs.
