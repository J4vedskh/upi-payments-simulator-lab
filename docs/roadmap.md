# Roadmap

The rolling branch `codex/daily-progress` is intended to make one real improvement per automation run.

## Completed

- [x] Add an opt-in JWT-secured payment submission profile while preserving the default local behavior.
- [x] Add validated configurable review and block amount thresholds while preserving the existing defaults.
- [x] Add bounded Prometheus counters for payment decisions and ledger record operations.
- [x] Align the OpenAPI contract with internal ledger ingestion and profile-aware payment authentication.

## Near-Term Improvements

| Priority | Improvement | Why It Matters |
| --- | --- | --- |
| P1 | Add PostgreSQL persistence for payments and ledger entries | Makes the simulator closer to real payment platforms |
| P1 | Add Testcontainers for Kafka integration tests | Verifies the event path beyond unit tests |
| P2 | Add OpenTelemetry tracing | Adds cross-service request and event correlation |
| P2 | Add GitHub Pages API rendering | Makes API docs easier to browse |
| P3 | Add Helm chart and Minikube walkthrough | Strengthens platform engineering presentation |

## Automation Rules

- Make one coherent, reviewable improvement per run.
- Commit only meaningful changes.
- Run relevant checks before committing.
- Keep the rolling PR updated instead of opening many small PRs.
- Report the commit hash, PR link, and verification result.

