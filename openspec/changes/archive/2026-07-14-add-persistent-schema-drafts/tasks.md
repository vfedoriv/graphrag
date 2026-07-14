## 1. Draft Persistence Model

- [x] 1.1 Add draft lifecycle, source type/status, analysis status, review state, origin, conflict, and compatibility enums
- [x] 1.2 Add Neo4j entities for schema drafts, source revisions, analysis runs, per-source results, aggregate revisions, decisions, and conflicts with optimistic versions
- [x] 1.3 Add repositories and ownership-scoped queries with indexes or constraints for draft lookup, run claims, active sources, and reuse keys
- [x] 1.4 Add persistence mapping tests and include new entities in Neo4j persistence-version compatibility/backfill coverage

## 2. Draft Lifecycle APIs

- [x] 2.1 Implement draft creation with managed knowledge-base validation, target identity rules, and optional associated base-schema validation
- [x] 2.2 Implement draft get/list/update operations with revision-aware conflict handling and read-only published behavior
- [x] 2.3 Implement guidance updates with canonical fingerprints, revision advancement, and invalidation of current-result eligibility
- [x] 2.4 Implement open-draft deletion guards and transactional cleanup orchestration
- [x] 2.5 Add lifecycle DTOs, controller operations, RFC 7807 mappings, and optimistic-concurrency tests

## 3. Draft Source Storage and Management

- [x] 3.1 Add draft source binary/text storage namespaces and transaction-aware storage mutation records
- [x] 3.2 Implement existing-document source addition with ownership validation and SHA-256 snapshotting
- [x] 3.3 Implement draft-file and pasted-text addition with validation, deduplication, metadata-only responses, and no `DocumentUpload` creation
- [x] 3.4 Implement source staleness/unavailability detection and explicit document-source refresh with revision history
- [x] 3.5 Implement analyzed-source logical removal/restoration and unanalysed-source physical cleanup
- [x] 3.6 Implement startup/storage reconciliation and tests for interrupted source create/delete operations
- [x] 3.7 Add source controller operations and tests for ownership isolation, duplicates, replacement, deletion, and privacy-safe logging

## 4. Durable Analysis Execution

- [x] 4.1 Add typed bounded executor configuration, queue limits, atomic run claiming, and one-running-analysis-per-draft enforcement
- [x] 4.2 Implement analysis snapshot creation covering draft/source/guidance/profile/prompt/settings/candidate revisions before background submission
- [x] 4.3 Adapt Phase 1 source preparation and candidate extraction for persisted source revisions and captured AI profile clients
- [x] 4.4 Implement exact reuse-key lookup and per-source result reuse without model calls
- [x] 4.5 Persist every source outcome independently and create deterministic completed or partial aggregate revisions
- [x] 4.6 Implement current-result promotion only for matching draft/source revisions
- [x] 4.7 Implement retry that reuses successful results and schedules only unresolved sources
- [x] 4.8 Implement startup recovery that closes interrupted runs while retaining reusable per-source results
- [x] 4.9 Add analysis start/status/retry APIs returning HTTP 202 and pageable per-source outcomes

## 5. Review, Conflicts, and Diffs

- [x] 5.1 Materialize base-schema elements as existing effective candidates without fabricating observed evidence
- [x] 5.2 Implement append-only accept, reject, modify, and pin decision persistence with current effective views
- [x] 5.3 Implement deterministic decision reapplication after reanalysis and conflict creation for coordinate or pinned-definition mismatches
- [x] 5.4 Implement explicit alternative/custom conflict resolution while retaining rejected alternatives and evidence
- [x] 5.5 Implement effective schema projection generation from aggregate, inherited elements, decisions, and resolved conflicts
- [x] 5.6 Implement deterministic base/prior-revision diffs with additive, review-required, and breaking classifications
- [x] 5.7 Add candidate, conflict, decision, projection, and diff APIs with pagination and revision preconditions
- [x] 5.8 Add review tests for multiple origins, evidence loss, pins, key conflicts, alias changes, and stable diff ordering

## 6. Observability, Recovery, and Integration

- [x] 6.1 Add metadata-first logs and workflow/source observations for draft lifecycle and background analysis
- [x] 6.2 Add tests proving draft source, guidance, candidate, schema, prompt, and model content stays out of normal logs
- [x] 6.3 Add multi-threaded tests for run claiming, stale mutations, queue overload, stale result promotion, and retry races
- [x] 6.4 Add Testcontainers integration coverage for the full create/add/analyze/review/reanalyze/diff/delete lifecycle
- [x] 6.5 Run `./mvnw test` and document draft API lifecycle, retention, polling, and retry semantics
