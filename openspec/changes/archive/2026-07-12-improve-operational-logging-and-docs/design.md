## Context

`LogSanitizer.preview` normalizes and truncates text but does not remove content. Multiple `INFO` log sites emit prompts, raw LLM responses, generated schemas, query previews, and graph extraction payloads independently of the observability content-capture setting. Testcontainers integration tests repeat these logs across contexts, making failures hard to inspect. Documentation also hard-codes versions that no longer match Maven properties.

## Goals / Non-Goals

**Goals:**
- Make normal operational logs safe for document and model-content workloads.
- Preserve identifiers, sizes, counts, latency, status, exception class, and tracing correlation needed to operate the service.
- Keep controlled AI trace capture independent from application logging.
- Reduce routine test output while preserving failures and application test diagnostics.
- Keep shared stack/configuration guidance verifiably aligned with the build.

**Non-Goals:**
- Remove AI observability or Langfuse-compatible capture.
- Guarantee that arbitrary third-party dependency logs never contain sensitive values.
- Change public API payloads or conceal actionable service errors from clients.

## Decisions

### Use metadata-first operational events

Normal application log levels record event name, correlation IDs, entity IDs, lengths, counts, durations, statuses, error classifications, and content fingerprints where useful. They do not record document text, prompts, query text, model responses, generated schemas, or extracted payloads. Rename or replace misleading preview helpers so call sites cannot mistake truncation for sanitization.

### Separate trace capture from application logs

Only the centralized observability service handles AI input/output capture according to its existing runtime privacy settings. Application logs record whether capture was enabled and stable observation identifiers, not captured content. Local diagnostic content output, if retained, is restricted to an explicit non-production diagnostic profile with a bounded allowlist.

### Configure test logs by category

Add test-only logging configuration that keeps application warnings/errors and test failures visible while reducing routine Spring, Neo4j schema-warning, Testcontainers, and payload logs. Tests assert behavior rather than relying on verbose output.

### Treat Maven properties as version documentation source

Documented Java, Spring Boot, Spring AI, and LangChain4j version facts are synchronized from `pom.xml` properties or checked by a lightweight regression test. Shared guidance is updated in README, AGENTS, and CLAUDE together.

## Risks / Trade-offs

- [Reduced raw diagnostic detail] → Preserve correlation IDs, error causes, bounded non-content metadata, and opt-in local diagnostics.
- [Tests can hide useful dependency warnings] → Keep warnings for unexpected categories and verify test configuration does not suppress application failures.
- [Documentation checks become brittle] → Check only designated version/configuration facts and use Maven properties as the canonical input.
- [Legacy logs have downstream consumers] → Publish a log-event migration note and preserve stable event identifiers where feasible.

## Migration Plan

1. Inventory content-bearing log sites and categorize events by operational purpose.
2. Add safe log helpers and test configuration, then migrate call sites with regression tests.
3. Update observability tests to prove trace capture behavior remains independent from log content.
4. Synchronize contributor documentation and add alignment checks before removing stale claims.

## Open Questions

- Which non-production environments, if any, may enable local content diagnostics?
- Is an audit log sink required for privileged troubleshooting instead of application logs?
