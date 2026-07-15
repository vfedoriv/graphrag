## 1. Evaluation Persistence and Contracts

- [x] 1.1 Add evaluation run/status, per-document outcome, metric, advisory assessment, and retry-lineage domain contracts
- [x] 1.2 Add Neo4j evaluation entities, repositories, ownership queries, run claims, optimistic versions, and required indexes
- [x] 1.3 Add evaluation request/status/result DTOs with held-out document selection and pageable outcomes
- [x] 1.4 Add persistence-version compatibility and repository mapping tests for evaluation entities

## 2. Held-Out Dry Evaluation

- [x] 2.1 Implement held-out eligibility checks against active evidence sources and knowledge-base ownership
- [x] 2.2 Implement evaluation snapshots for draft projection/decisions, document hashes, profile revision, prompt revision, and settings
- [x] 2.3 Implement dry document parsing/chunking/extraction that performs no embedding, graph, chunk, or extraction-run writes
- [x] 2.4 Implement deterministic recognized/unknown, dropped-relationship, key-availability, type-conflict, required-property, low-support, and unsupported-guidance metrics with not-applicable rates
- [x] 2.5 Implement optional advisory intended-question coverage and schema-noise assessment with reproducibility metadata
- [x] 2.6 Implement durable per-document progress, partial results, exact-match reuse, retry, and startup interruption recovery
- [x] 2.7 Add evaluation start/status/retry APIs returning HTTP 202 and tests for staleness, partial failure, and privacy-safe logging

## 3. Publication Readiness

- [x] 3.1 Implement revision-specific readiness calculation for registry validation, blocking conflicts, pending required guidance, target identity, and evaluation policy
- [x] 3.2 Add readiness responses containing projection content hash and complete blocking reason identifiers
- [x] 3.3 Add readiness tests for stale evaluations, unresolved keys/types/directions, guided candidates without evidence, and schema validation failures

## 4. Atomic Publication

- [x] 4.1 Add publication claim/link persistence with uniqueness by draft and target schema identity
- [x] 4.2 Add a schema-registry application boundary that validates and creates the inactive generated schema plus knowledge-base association transactionally
- [x] 4.3 Implement publish revision/hash preconditions, atomic draft transition, and idempotent repeated responses
- [x] 4.4 Preserve the publication content hash and report drift when the published inactive schema is later edited
- [x] 4.5 Ensure publication never activates a schema or starts document processing
- [x] 4.6 Add publication controller operations and race, rollback, retry, editable-inactive, and active-schema guard tests

## 5. Reprocessing Plan Persistence and APIs

- [x] 5.1 Add reprocessing plan/item status, snapshot, progress, failure, and retry-lineage entities and repositories
- [x] 5.2 Implement plan creation for all or selected owned documents with active published-schema and content-hash validation
- [x] 5.3 Add typed bounded plan executor configuration, atomic claims, independent item commits, and startup recovery
- [x] 5.4 Implement pre-item document SHA-256 and active-target-schema checks with stale or blocked outcomes
- [x] 5.5 Invoke existing document processing with overwrite enabled while preserving processing options, cleanup, run history, profile routing, and prior-success behavior
- [x] 5.6 Implement plan terminal status/progress aggregation and pageable per-document results
- [x] 5.7 Implement retry that links the prior plan, skips matching successes, and explicitly resnapshots unresolved items
- [x] 5.8 Add plan create/status/retry APIs returning HTTP 202 and tests for active-schema changes, document replacement, partial failure, and restart

## 6. End-to-End Verification and Documentation

- [x] 6.1 Add metadata-first logs and AI observations for evaluation, publication, and reprocessing orchestration
- [x] 6.2 Add tests proving normal logs contain no held-out text, prompts, model output, draft projection, generated schema, or extracted graph payloads
- [x] 6.3 Add an end-to-end Testcontainers flow covering draft evaluation, readiness, publication, explicit activation, reprocessing progress, failure, and retry
- [x] 6.4 Verify existing schema update/delete behavior remains available for the published schema while inactive and rejected while active
- [x] 6.5 Run `./mvnw test` and document metric formulas, advisory labels, publication drift, explicit activation, and plan recovery semantics
