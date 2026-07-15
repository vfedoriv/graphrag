# schema-draft-analysis Specification

## Purpose
TBD - created by archiving change add-persistent-schema-drafts. Update Purpose after archive.
## Requirements
### Requirement: Draft analysis is a durable asynchronous operation
The system SHALL start draft analysis as a durable run, return an accepted response with a run identifier and status location, and process active sources with bounded background concurrency.

#### Scenario: Analysis starts
- **WHEN** a client starts analysis for an open draft with at least one eligible active source and no other running analysis
- **THEN** the system snapshots the draft revision, guidance revision, active source revisions, AI profile identifier and revision, prompt revision, and analysis settings
- **AND** it returns the durable run in `RUNNING` state without waiting for all model calls

#### Scenario: Concurrent analysis is requested
- **WHEN** a draft already has a running analysis and a client requests another run
- **THEN** the system returns the existing matching run when the requested snapshot is identical
- **AND** otherwise rejects the request as a conflict

#### Scenario: Run status is read
- **WHEN** a client retrieves an analysis run
- **THEN** the response includes aggregate status, snapshot metadata, source counts, per-source outcomes, timestamps, current-result flag, and privacy-safe failure details

### Requirement: Source analysis results are idempotently reusable
The system SHALL reuse a completed source-analysis result only when the draft source content fingerprint and revision, guidance fingerprint and revision, AI profile identifier and revision, prompt revision, and analysis settings fingerprint all match.

#### Scenario: Unchanged source is analyzed again
- **WHEN** a new run includes a source with a completed result under an identical reuse key
- **THEN** the system reuses the result without another model call
- **AND** records that the source outcome was reused

#### Scenario: Profile or prompt changes
- **WHEN** the active AI profile revision or candidate prompt revision differs from the completed source result
- **THEN** the result is not reused
- **AND** the source is analyzed under the new snapshot

#### Scenario: Guidance changes
- **WHEN** the guidance fingerprint or revision differs from a prior completed source result
- **THEN** the prior result remains auditable but is not reused for the new run

#### Scenario: Document is replaced in place
- **WHEN** a document source identifier is unchanged but its source revision or content fingerprint changed through explicit refresh
- **THEN** the prior source result is not reused for replacement content

### Requirement: Analysis persists incremental progress and supports retry
The system SHALL persist each source outcome independently so interruption or partial failure does not discard completed source work.

#### Scenario: All eligible sources succeed
- **WHEN** every source in the analysis snapshot completes or is validly reused
- **THEN** the run becomes `COMPLETED`
- **AND** the system persists a deterministic aggregate revision and per-source results

#### Scenario: Some sources fail
- **WHEN** at least one source succeeds and at least one source fails
- **THEN** the run becomes `PARTIAL`
- **AND** successful results and the partial aggregate remain available

#### Scenario: All sources fail
- **WHEN** no source completes successfully or is reusable
- **THEN** the run becomes `FAILED`
- **AND** it does not replace the draft's previously current aggregate

#### Scenario: Failed sources are retried
- **WHEN** a client retries a partial or failed run
- **THEN** a new run reuses matching successful source results and executes only failed, interrupted, stale, or newly eligible sources

#### Scenario: Application restarts during a run
- **WHEN** the application starts with a run still marked `RUNNING`
- **THEN** the interrupted run is closed with a retryable interruption outcome
- **AND** already completed source results remain eligible for reuse by retry

### Requirement: Only a matching draft snapshot becomes current
The system SHALL promote a completed or partial aggregate as the draft's current analysis result only when the analyzed draft revision and source membership still match the current draft state.

#### Scenario: Draft is unchanged while analysis runs
- **WHEN** analysis finishes and its draft revision and active source revisions still match
- **THEN** the resulting aggregate revision becomes current

#### Scenario: Draft changes while analysis runs
- **WHEN** guidance, source membership, or a source revision changes before a run finishes
- **THEN** the run and its results remain auditable
- **AND** the run is marked non-current
- **AND** it does not overwrite the current aggregate or review decisions

### Requirement: Background analysis uses the captured AI profile safely
The system SHALL resolve the knowledge base active AI profile at run creation, retain the resolved client for that run, and observe every background model call through existing privacy controls.

#### Scenario: Knowledge base has no active profile
- **WHEN** analysis is requested and no active AI profile can be resolved
- **THEN** the request fails before a run or model side effect is created

#### Scenario: Profile assignment changes during a run
- **WHEN** the knowledge base active profile assignment changes after a run starts
- **THEN** the running analysis continues with its captured profile client and revision
- **AND** a later run uses the newly active profile snapshot

### Requirement: Analysis source outcomes use the standard page envelope
The system SHALL represent the selected source-outcome slice in an analysis status response as a typed page envelope with zero-based page number, bounded page size, total element count, and ordered content.

#### Scenario: Client polls a paged analysis status
- **WHEN** a client requests an analysis run with page and size parameters
- **THEN** the response includes the requested bounded source-outcome page as `page`, `size`, `totalElements`, and `content`
- **AND** aggregate run status and counts describe the entire run rather than only the selected page

#### Scenario: Requested page has no outcomes
- **WHEN** the requested page is beyond the available source outcomes
- **THEN** the response returns empty content with the stable page metadata and full total element count
