## Context

Advanced-search rows already persist `queryText`, requested evidence, evidence-text preference, profile/schema snapshots, and lifecycle state. The API projects every run through one summary DTO that omits the request context, while persisted results are validated as JSON but returned as `JsonNode`. Evidence retains durable technical provenance but not the relational document name users need for citations. Admission currently checks request bounds and queue capacity but defers provider and embedding failures to asynchronous processing.

The design must preserve owner scoping, payload version 1, partial and abstaining results, metadata-first logging, and schema-optional graph behavior. It must not require a provider network call during readiness checks.

## Goals / Non-Goals

**Goals:**

- Make run history recognizable without exposing full queries in list responses.
- Publish an OpenAPI-describable version-1 result without changing its JSON envelope.
- Make persisted citations understandable after document metadata changes.
- Share deterministic readiness evaluation between preflight reads and run admission.
- Return stable machine-readable admission blockers while preserving privacy-safe logs.

**Non-Goals:**

- Restore or emulate the retired Hybrid Search endpoint.
- Add inline answer segments or alter synthesis prompts to position citations in answer prose.
- Require an active schema, reject an empty corpus, or probe external AI providers over the network.
- Redesign internal retrieval/ranking algorithms or change result retention.

## Decisions

### Split run summaries from owned run details

List responses use a `RunSummaryResponse` with a Unicode-safe, whitespace-normalized `queryPreview` bounded to 160 code points plus `maximumEvidence` and `includeEvidenceText`. Create and `GET /{runId}` return a `RunDetailResponse` that adds the full `query`. Both retain existing lifecycle fields and links.

Using distinct DTOs keeps the history contract intentionally bounded and lets OpenAPI describe where full query content appears. Reusing one DTO with a nullable query was rejected because null would ambiguously mean redaction, absence, or an older row.

### Represent payload version 1 with explicit API DTOs

`ResultResponse` exposes a concrete `AdvancedSearchResultV1` containing typed answer, confidence, limitation, claim, source-range, evidence/context, graph-fact, answer-diagnostics, and pipeline-diagnostics records. Pipeline diagnostics use explicit nested DTOs for plan, sufficiency, follow-up, attempts, fusion, rerank, and selection; bounded extension maps are permitted only at named forward-compatible diagnostic leaves, not as the top-level public contract.

The relational result remains canonical JSON with `payloadVersion=1`. The codec deserializes it into the typed version-1 model and validates the same referential constraints before returning it. This avoids a database migration and preserves the existing wire property names. Unknown stored payload versions fail explicitly rather than being coerced into version 1.

### Snapshot source display metadata during result assembly

Before result persistence, collect distinct evidence/context `documentId` values and batch-load owned PostgreSQL document metadata. Add immutable `sourceFilename`, `sourceContentType`, and `sourceDisplayLabel` to each evidence/context entry. The display label initially equals the original filename and exists as a stable evolution point.

The snapshot belongs to the run result; result reads never join current document rows. Missing metadata during assembly produces a sanitized limitation/diagnostic and a deterministic fallback display label based on document identity rather than dropping otherwise valid evidence.

### Use one deterministic readiness evaluator

Add `GET /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs/readiness`. A shared service returns `ready`, profile identity/revision, graph-branch availability, embedded-chunk presence, and stable blocker objects with `code` and safe `description`.

Blocking checks are local and deterministic:

- the active profile resolves and has structurally usable chat-provider configuration;
- when embedded chunks exist, the embedding client/configuration is constructible and the active embedding space is compatible with stored chunks.

`SCHEMA_UNAVAILABLE` is informational and disables the graph branch. `EMPTY_CORPUS` is informational and permits a run that can publish insufficient evidence. Provider reachability, credentials accepted by a remote service, and transient network health are not probed.

Create evaluates readiness before reserving queue capacity, then snapshots the same profile revision when persisting the run. A blocker raises a dedicated conflict exception whose RFC 7807 response has a stable problem type and `blockers` array. Logs contain only knowledge-base/profile identifiers and blocker codes.

### Treat examples as executable contract fixtures

Controller/OpenAPI tests serialize the documented request using `maximumEvidence` and validate completed, partial, insufficient-evidence, and answer-unavailable result fixtures against the generated schema. A repository-wide contract check ensures removed endpoint guidance does not advertise Hybrid Search and replacement examples use current field names.

## Risks / Trade-offs

- [A provider can pass local readiness but fail remotely] → Define readiness as configuration compatibility, keep asynchronous provider failures and partial-result behavior, and avoid expensive or side-effecting health probes.
- [Existing stored results lack source metadata] → Preserve readable version-1 deserialization with nullable snapshot fields for legacy rows; all newly persisted results populate them.
- [Typed diagnostics drift from internal records] → Centralize result mapping and add serialization fixtures for every terminal answer shape.
- [Query previews leak into logs through response debugging] → Keep operational logging metadata-only and test that query/preview content is absent from normal logs.
- [Profile changes between readiness and persistence] → Snapshot and verify profile identity/revision in the creation transaction; retry or reject on a changed target.

## Migration Plan

1. Introduce typed version-1 DTOs and legacy-compatible deserialization tests.
2. Add source metadata enrichment for newly created results.
3. Add summary/detail response types and readiness evaluation.
4. Gate admission with the evaluator and stable conflict problems.
5. Update OpenAPI examples, migration guidance, and contract fixtures.
6. Roll back by disabling admission gating and returning the prior projection; stored JSON remains compatible and new source fields are additive.

## Open Questions

None.
