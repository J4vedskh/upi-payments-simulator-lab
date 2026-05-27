# Daily Progress

This page tracks the purpose and guardrails for the rolling automation branch. The branch is intentionally long-lived so each daily improvement can build on the last one without creating noisy one-off pull requests.

## Branch Contract

| Item | Value |
| --- | --- |
| Branch | `codex/daily-progress` |
| Schedule | Daily at 4:00 PM IST |
| Duration | 30 runs |
| Pull request style | One rolling PR against `main` |

## Improvement Queue

The automation should choose one coherent task per run from the roadmap or from code it discovers while working.

| Theme | Example Improvements |
| --- | --- |
| Features | Persistence, richer fraud rules, payment status transitions |
| Tests | Kafka integration tests, controller tests, contract validation |
| Docs | Diagram refinements, API examples, runbook updates |
| Platform | Docker image publishing, Helm chart, CI hardening |
| Observability | Metrics, tracing, dashboards, operational alerts |

## Verification Standard

Every run should report:

- commit hash
- pull request link
- checks executed
- result of each check
- any skipped check with a clear reason

