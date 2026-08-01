## 1. Typed Advanced-Search Results

- [x] 1.1 Define explicit version-1 API DTOs for answers, claims, confidence, limitations, evidence/contexts, source ranges, graph facts, answer diagnostics, and all published pipeline diagnostics
- [x] 1.2 Refactor `AdvancedSearchResultCodec` to deserialize, validate, and return the typed version-1 payload while rejecting unsupported versions and preserving the existing JSON property shape
- [x] 1.3 Add serialization fixtures for completed, partial, insufficient-evidence, answer-unavailable, legacy-source-metadata, and unsupported-version results

## 2. Durable Citation Metadata

- [x] 2.1 Add a bounded batch lookup for owned document citation metadata keyed by distinct evidence/context document identifiers
- [x] 2.2 Enrich newly assembled evidence and context records with snapshotted filename, content type, and display label before result persistence
- [x] 2.3 Preserve legacy version-1 result readability with nullable source metadata and test document replacement/deletion history behavior

## 3. Discoverable Run Request Context

- [x] 3.1 Introduce separate run-summary and run-detail DTOs with applied evidence options, bounded Unicode-safe query previews, and full query only on create/detail responses
- [x] 3.2 Update create, list, status, and cancellation mappings and OpenAPI schemas without changing ownership, paging, lifecycle, or retention behavior
- [x] 3.3 Add controller/integration tests for preview bounds, full-query visibility, applied defaults, foreign ownership, and query-free operational logs

## 4. Readiness and Fail-Fast Admission

- [x] 4.1 Implement a shared deterministic readiness evaluator for profile resolution, structurally usable chat/embedding configuration, embedded-corpus state, embedding compatibility, and graph-branch availability
- [x] 4.2 Add the owned readiness endpoint with stable blocker/informational codes and privacy-safe typed responses
- [x] 4.3 Enforce the same readiness policy before queue reservation and run persistence, including profile revision verification and capacity release safety
- [x] 4.4 Add a dedicated RFC 7807 conflict type/handler and tests proving blocker parity, no durable work on rejection, schema-optional text-only admission, and empty-corpus insufficient-evidence admission

## 5. Contract Documentation and Verification

- [x] 5.1 Correct controller and migration examples from `maxEvidence` to `maximumEvidence` and keep Hybrid Search absent from the public API
- [x] 5.2 Add generated-OpenAPI assertions that result version 1 is typed and request/run/readiness examples match actual serialization
- [x] 5.3 Run focused advanced-search unit/controller/integration tests, the fast test profile, and `graphify update .`
