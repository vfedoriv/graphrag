# schema-reprocessing-plans Specification

## Purpose
TBD - created by archiving change add-schema-draft-validation-and-publication. Update Purpose after archive.
## Requirements
### Requirement: Reprocessing plans are explicit post-activation resources
The system SHALL allow a client to create a durable reprocessing plan only for a published schema that is currently active for the same knowledge base, selecting either all eligible documents or an explicit owned document set.

#### Scenario: Create a plan for all documents
- **WHEN** a client requests a plan for all eligible documents after explicitly activating the published schema
- **THEN** the system snapshots the knowledge base, target schema identifier and content hash, active AI profile revision, and eligible document identifiers and SHA-256 values
- **AND** returns an accepted response with plan status and progress location

#### Scenario: Target schema is not active
- **WHEN** a client requests a plan for a schema that is associated but not currently active for the knowledge base
- **THEN** the system rejects plan creation as a conflict
- **AND** no document processing begins

#### Scenario: Selected document is not owned by the knowledge base
- **WHEN** an explicit plan contains a missing or foreign document identifier
- **THEN** the system rejects plan creation using established ownership-safe behavior

### Requirement: Plans reuse existing overwrite processing semantics
The system SHALL process each plan document through the existing document-processing workflow with overwrite enabled and SHALL preserve existing processing-run, cleanup, embedding-space, active-profile, and failure semantics.

#### Scenario: Document is reprocessed successfully
- **WHEN** plan execution processes a document successfully
- **THEN** the document has a new active completed processing run under the active target schema
- **AND** prior completed runs become inactive according to existing overwrite behavior

#### Scenario: Document reprocessing fails
- **WHEN** plan execution fails for a document
- **THEN** the per-document plan outcome records a privacy-safe failure
- **AND** the existing previous active completed processing run remains active when applicable
- **AND** other eligible plan documents may continue

#### Scenario: Source document changes before processing
- **WHEN** a document's current SHA-256 differs from the plan snapshot before its turn executes
- **THEN** that document is marked stale and is not processed under the old snapshot

### Requirement: Plan progress and retry are durable
The system SHALL persist plan status, counts, per-document outcomes, timestamps, and retry lineage, using bounded concurrency and independently committed document outcomes.

#### Scenario: Plan is read while running
- **WHEN** a client retrieves a running plan
- **THEN** the response includes total, queued, running, succeeded, failed, stale, and blocked document counts
- **AND** per-document outcomes are pageable

#### Scenario: Plan completes with failures
- **WHEN** at least one document succeeds and at least one fails, becomes stale, or is blocked
- **THEN** the plan becomes `PARTIAL`
- **AND** successful document outcomes are retained

#### Scenario: Plan is retried
- **WHEN** a client retries a partial or interrupted plan
- **THEN** a new plan references the prior plan
- **AND** already successful matching document snapshots are not processed again
- **AND** unresolved documents are resnapshotted only through explicit retry behavior

#### Scenario: Application restarts during a plan
- **WHEN** the application starts with running document outcomes
- **THEN** interrupted outcomes become retryable failures
- **AND** completed document processing runs and plan outcomes remain consistent

### Requirement: Active schema changes stop unsafe plan work
The system SHALL verify the target schema remains active and retains the snapshotted content hash before starting each queued document.

#### Scenario: Another schema becomes active during a plan
- **WHEN** the knowledge base active schema changes before a queued document starts
- **THEN** the remaining queued documents become blocked
- **AND** the plan becomes partial or failed according to completed outcomes
- **AND** no blocked document is processed under the unintended schema

#### Scenario: Active schema content cannot change
- **WHEN** the target schema remains active
- **THEN** existing active-schema mutation guards prevent its content hash from changing during plan execution

### Requirement: Reprocessing logs remain metadata-first
The system SHALL log plan orchestration using identifiers, hashes, counts, statuses, timings, and exception classes without logging document text, extracted graph content, prompts, or model responses.

#### Scenario: Plan completes
- **WHEN** a plan reaches a terminal status
- **THEN** normal logs include aggregate outcome counts and elapsed time
- **AND** AI content remains governed only by existing controlled observation behavior inside document processing

### Requirement: Reprocessing items use the standard page envelope
The system SHALL represent the selected per-document item slice in a reprocessing plan response as a typed page envelope with zero-based page number, bounded page size, total element count, and deterministic content order.

#### Scenario: Client polls a paged reprocessing plan
- **WHEN** a client requests a reprocessing plan with page and size parameters
- **THEN** the response includes the bounded item page as `page`, `size`, `totalElements`, and `content`
- **AND** aggregate queued, running, succeeded, failed, stale, and blocked counts describe the entire plan

#### Scenario: Plan contains no items on the selected page
- **WHEN** the selected page has no matching plan items
- **THEN** the response returns empty content while preserving the requested page metadata and total element count

### Requirement: Reprocessing plan history is discoverable by knowledge base and draft
The system SHALL provide a paginated knowledge-base reprocessing-plan history that can be filtered by an owned draft and whose summaries include identifiers, statuses, document counts, timestamps, retryability, retry lineage, latest status, and current target-schema validity.

#### Scenario: Client recovers a plan after losing the identifier
- **WHEN** a client lists reprocessing plans for a knowledge base and filters by an owned draft
- **THEN** the system returns a bounded page ordered by creation time descending with deterministic ties
- **AND** each summary contains enough state to resume polling or open the detailed plan resource

#### Scenario: Latest plan is identified
- **WHEN** more than one plan exists for a draft
- **THEN** only the most recently created plan is marked latest
- **AND** older plans and retry lineage remain visible in history

#### Scenario: Plan target is no longer active
- **WHEN** a plan's target schema or snapshotted content hash no longer matches the knowledge base active schema
- **THEN** the summary reports that the target is not current
- **AND** retryability reflects whether the retry command would be accepted under current target state

#### Scenario: Foreign draft filter is supplied
- **WHEN** a client filters plan history with a draft not owned by the knowledge base
- **THEN** the system rejects the request using established ownership-safe not-found behavior

### Requirement: Reprocessing plans and items are relational
The system SHALL persist bounded plan identity, schema/knowledge-base ownership, item document identity, status, claims, retries, counters, timestamps, and optimistic versions in PostgreSQL.

#### Scenario: Activation creates a plan
- **WHEN** schema activation requires overwrite reprocessing
- **THEN** one bounded item is created for each eligible document
- **AND** duplicate items for the same plan and document are prevented

#### Scenario: A worker claims an item
- **WHEN** an eligible pending or retryable item is claimed
- **THEN** a conditional relational update assigns one owner
- **AND** concurrent workers cannot process the same claim

### Requirement: Reprocessing recovery repairs derived state
The system SHALL recover expired claims and SHALL be able to recompute plan counters from authoritative item state.

#### Scenario: A worker stops after document processing
- **WHEN** the item completion checkpoint is missing
- **THEN** recovery inspects the underlying document-run outcome
- **AND** completes or retries the item idempotently

#### Scenario: Stored counters disagree with items
- **WHEN** recovery detects inconsistent plan counters
- **THEN** counters are repaired from item states
