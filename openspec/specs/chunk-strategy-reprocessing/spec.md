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

### Requirement: Chunk migration creation is bound to previewable target state
Chunk migration creation SHALL recompute the same target and selection policy exposed by preview and SHALL require the client-supplied `expectedChunkerRevision` to match the current effective target.

#### Scenario: Previewed target remains current
- **WHEN** a client creates a plan with the revision returned by preview and all blockers remain clear
- **THEN** the durable plan snapshots the recomputed target and selected documents

#### Scenario: Readiness changes after preview
- **WHEN** a blocker appears before plan creation
- **THEN** creation returns `409 Conflict` and creates no plan or items

### Requirement: Retry policy uses a closed mode
The retry API SHALL accept the closed mode `RESNAPSHOT_UNRESOLVED`, preserve prior successful items, and resnapshot only unresolved documents under the then-current target.

#### Scenario: Explicit retry mode is submitted
- **WHEN** an eligible terminal plan is retried with `mode=RESNAPSHOT_UNRESOLVED`
- **THEN** a linked plan is created for the newly snapshotted unresolved targets only

#### Scenario: Unsupported retry mode is submitted
- **WHEN** the request contains an unknown retry mode
- **THEN** the system returns `400 Bad Request` and creates no plan

#### Scenario: Deprecated valid boolean is submitted during compatibility
- **WHEN** a legacy client submits `resnapshotUnresolvedDocuments=true` without a mode during the compatibility window
- **THEN** the system treats it as `RESNAPSHOT_UNRESOLVED` and marks the boolean deprecated in OpenAPI

#### Scenario: Unsupported false boolean is submitted
- **WHEN** a legacy client submits `resnapshotUnresolvedDocuments=false`
- **THEN** the system returns `400 Bad Request` without implying that retry without resnapshot is supported
