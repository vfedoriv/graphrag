## 1. Authoritative Chunking State

- [x] 1.1 Define typed chunking-state DTOs for canonical values/sources, component revisions, tokenizer/count mode, settings hash, effective revision, lifecycle, and compatibility aliases
- [x] 1.2 Build the state service from existing typed runtime accessors and revision calculators without introducing ad hoc string setting reads
- [x] 1.3 Add `GET /api/v1/chunking-state` with OpenAPI examples and tests for overrides, defaults, alias precedence, and post-update refetch behavior

## 2. Shared Migration Target and Preview

- [x] 2.1 Extract side-effect-free migration target resolution, selection validation, document-specific classification, and stable blocker evaluation from plan creation
- [x] 2.2 Add typed preview request/response models with target identity, readiness, blockers, whole-KB classification counts, selected totals, and bounded document pages
- [x] 2.3 Add the knowledge-base chunk-migration preview endpoint for `OUTDATED_STRATEGY`, `DOCUMENT_IDS`, and `ALL` without reserving or persisting work
- [x] 2.4 Refactor plan creation to reuse the shared classifier and continue transactionally enforcing the previewed `expectedChunkerRevision` and destructive-plan exclusion
- [x] 2.5 Add ownership, processing-option, empty/outdated/current classification, paging, stale-target, blocker-parity, and zero-side-effect integration tests

## 3. Reprocessing History Filters

- [x] 3.1 Extend controller and repository-port contracts with optional `reason`, `selection`, and `status` filters combinable with `draftId`
- [x] 3.2 Implement one relational filtered page/count query with deterministic `createdAt DESC, id DESC` ordering and explicit null-selection behavior
- [x] 3.3 Add tests for each filter, combined filters, foreign draft ownership, unfiltered compatibility, exact totals, ties, and empty pages

## 4. Closed Retry Contract

- [x] 4.1 Introduce `RESNAPSHOT_UNRESOLVED` as the canonical retry mode and implement exhaustive service handling
- [x] 4.2 Add a deprecated compatibility mapper for the previously valid `resnapshotUnresolvedDocuments=true` body and reject false, unknown, or conflicting requests
- [x] 4.3 Update OpenAPI examples and add retry tests for schema and chunk plans, prior-success preservation, unresolved resnapshotting, lineage, and invalid modes

## 5. Compatibility and Verification

- [x] 5.1 Keep existing runtime-setting aggregate metadata and mutation envelopes compatible while documenting the dedicated state resource as authoritative
- [x] 5.2 Run focused runtime-settings and reprocessing unit/integration tests, the fast test profile, relevant Testcontainers tests with escalation, and `graphify update .`
