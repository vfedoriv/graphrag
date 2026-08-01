## Why

Advanced-search runs persist enough information to support durable history, but their public API omits the submitted request context, exposes a largely untyped result, lacks durable source labels, and accepts work before checking provider and embedding readiness. These gaps force frontend clients to reconstruct or validate backend-owned contracts and turn preventable admission failures into delayed run failures.

## What Changes

- Add a bounded query preview and applied evidence options to run history/status responses, with the full query returned only by the owned run-detail resource.
- Replace the public version-1 result `JsonNode` with explicit versioned DTOs for answers, claims, confidence, limitations, evidence, source ranges, graph facts, answer diagnostics, and retrieval/ranking diagnostics while retaining `payloadVersion` as the evolution boundary.
- Snapshot citation-safe source metadata, including original filename and content type, into result evidence and context entries.
- Add an owned advanced-search readiness resource and enforce the same readiness policy during admission before capacity is reserved or a durable run is created.
- Reject admission when the active provider configuration is unusable or stored embeddings are incompatible with the active profile; continue allowing schema-free text-only search and empty-corpus runs that complete with insufficient evidence.
- Return readiness failures as RFC 7807 `409 Conflict` responses with stable blocker codes, without exposing credentials or user content in operational logs.
- Correct request examples and migration guidance to use `maximumEvidence`, and add contract/OpenAPI regression coverage for documented payloads and endpoint evolution.
- Keep the retired Hybrid Search route removed; add no compatibility adapter.
- Defer inline answer segments and answer-text passage mapping to a future change.

## Capabilities

### New Capabilities
- `advanced-search-readiness`: Defines owner-scoped readiness inspection and fail-fast run admission using stable blocker codes.

### Modified Capabilities
- `advanced-search-runs`: Adds discoverable request context to history/detail resources and readiness-aware admission behavior.
- `advanced-search-answering`: Makes the version-1 public result contract explicit and snapshots durable human-readable citation metadata.
- `documentation-alignment`: Keeps OpenAPI examples and endpoint-migration guidance aligned with the implemented advanced-search request contract.

## Impact

This affects advanced-search controllers, DTOs, run admission, result serialization/validation, citation assembly, document metadata lookup, RFC 7807 handling, generated OpenAPI, repository documentation, and focused unit/integration/serialization tests. Response changes are additive except that the Java/OpenAPI type of the existing version-1 `result` body becomes explicit while preserving its JSON shape and `payloadVersion`.
