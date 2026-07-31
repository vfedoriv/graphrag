# chunk-strategy-reprocessing Specification

## Purpose
Define explicit, durable migration of previously processed documents to the current chunking strategy while preserving immutable processing targets and safe retry behavior.

## Requirements
### Requirement: Typed chunk migration plan creation
The knowledge-base reprocessing resource SHALL accept `CHUNK_STRATEGY_MIGRATION` with `OUTDATED_STRATEGY`, non-empty owned `DOCUMENT_IDS`, or forced `ALL` selection and SHALL require `expectedChunkerRevision`.

#### Scenario: Outdated selection
- **WHEN** a valid request selects `OUTDATED_STRATEGY`
- **THEN** the plan includes owned documents with no chunks or a persisted effective revision different from the requested current revision

#### Scenario: Stale expected revision
- **WHEN** `expectedChunkerRevision` differs from the effective server revision
- **THEN** plan creation returns RFC 7807 `409 Conflict` and queues no items

### Requirement: Immutable migration target snapshot
Plan creation SHALL atomically snapshot canonical typed chunk settings, chunker/tokenizer/header/parser revisions, AI profile and embedding space, active schema, processing options, selected document IDs, and source-content hashes.

#### Scenario: Live setting changes after creation
- **WHEN** a live setting changes after a plan is accepted
- **THEN** workers continue only when the immutable target remains current and never substitute the later value into the plan

### Requirement: Target and source change outcomes
The worker SHALL mark a changed selected document as `STALE_SOURCE` and SHALL mark queued work as `BLOCKED_TARGET_CHANGED` when any snapshotted chunk, profile, embedding-space, or active-schema target is no longer current.

#### Scenario: Binary replaced before claim
- **WHEN** a selected document's content hash no longer matches the snapshot
- **THEN** that item becomes `STALE_SOURCE` without overwriting the replacement

#### Scenario: Profile changes mid-plan
- **WHEN** the active profile or embedding-space target changes after some items complete
- **THEN** queued items become `BLOCKED_TARGET_CHANGED` and completed items remain committed and visible

### Requirement: Durable progress and linked retry
Chunk migration plans SHALL reuse durable item claims, progress, history, recovery, and linked retry, and retry SHALL explicitly resnapshot unresolved documents into a new plan.

#### Scenario: Partial plan retry
- **WHEN** an operator retries a partial migration
- **THEN** a linked plan is created only for explicitly resnapshotted unresolved documents under the then-current target
