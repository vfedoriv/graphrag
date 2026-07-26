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
The system SHALL persist each source outcome independently so interruption or partial failure does not discard completed source work, and SHALL retain privacy-safe progress and failure diagnostics for a failed source.

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

#### Scenario: Prepared source fails during candidate analysis
- **WHEN** source preparation produced analysis chunks and candidate model invocation or conversion later fails
- **THEN** the failed source outcome records the number of prepared chunks rather than zero
- **AND** the operational warning includes the exception type and a non-reversible exception-message fingerprint
- **AND** the warning does not include source content, prompts, normal or reasoning model output, or candidate payloads

#### Scenario: Source fails before preparation completes
- **WHEN** a source fails before any prepared analysis chunks are available
- **THEN** the failed source outcome records a chunk count of zero
- **AND** it retains the privacy-safe failure category and retryability metadata

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

### Requirement: Analysis run history is discoverable and lineage-aware
The system SHALL provide a paginated analysis-run history for an owned draft whose summaries include identifiers, statuses, source counts, timestamps, retryability, retry parent, aggregate identifier, and derived currentness.

#### Scenario: Client recovers analysis after losing the run identifier
- **WHEN** a client lists analysis runs for an owned draft
- **THEN** the system returns a bounded page ordered by creation time descending with deterministic ties
- **AND** each summary contains enough state to resume polling or open the detailed status resource

#### Scenario: Running analysis still matches the draft
- **WHEN** a queued or running analysis snapshot matches the current draft revision, guidance, and active source membership
- **THEN** its summary is marked current

#### Scenario: Completed analysis produced the current aggregate
- **WHEN** a terminal analysis run's aggregate identifier equals the draft's current aggregate identifier
- **THEN** its summary is marked current even if an older persisted flag is inconsistent

#### Scenario: Draft changed after analysis
- **WHEN** an analysis snapshot and aggregate no longer match current draft state
- **THEN** its history remains visible
- **AND** its summary is marked non-current

#### Scenario: Analysis is retried
- **WHEN** a client retries a terminal analysis run and a new run is created
- **THEN** the new run records the selected run identifier as `retryOfRunId`
- **AND** historical runs created before lineage support remain valid roots with a null parent

### Requirement: Draft source failures use structured recovery decisions
The system SHALL classify each failed draft source using a compatible broad failure category, a stable detailed failure code, and an independently determined retryability value based on the bounded exception cause chain and failure stage.

#### Scenario: Nested transport timeout is exhausted
- **WHEN** a source model call fails with a timeout represented by an outer provider exception whose bounded cause chain contains a socket, connection, or read timeout
- **THEN** the source outcome is classified as a retryable timeout
- **AND** the detailed failure code identifies a transport timeout
- **AND** classification does not depend on the outer exception message containing the word `timeout`

#### Scenario: Provider status is available
- **WHEN** the OpenAI-compatible SDK exposes an HTTP status for a failed source call
- **THEN** rate-limit and retryable provider-service statuses receive retryable detailed codes
- **AND** authentication, authorization, invalid-request, and other permanent statuses receive non-retryable detailed codes

#### Scenario: Source state is invalid
- **WHEN** a source snapshot is stale or its content is unavailable before candidate analysis can complete
- **THEN** the source outcome receives a non-retryable source-state failure code
- **AND** a later model output retry is not attempted

### Requirement: Draft candidate analysis retries unusable model output once
The system SHALL make at most one additional application-level output attempt when a completed transport call returns missing, blank, malformed, or candidate-contract-invalid model output, and SHALL NOT add another application retry for transport or provider failures already handled by the SDK.

#### Scenario: First response has blank normal content
- **WHEN** the first model response has no usable normal assistant content
- **THEN** the system makes one additional output attempt for that chunk when the source remains eligible
- **AND** reasoning metadata is not interpreted as candidate output

#### Scenario: Second response is valid
- **WHEN** the first output attempt is unusable and the second output attempt converts and validates successfully
- **THEN** the source succeeds using only candidates from the successful attempt
- **AND** the failed attempt contributes no candidate or alias data

#### Scenario: Both responses are unusable
- **WHEN** both allowed output attempts fail conversion or candidate-contract validation
- **THEN** the source receives a retryable model-output failure outcome
- **AND** no further application-level output attempt is made

#### Scenario: SDK transport retries are exhausted
- **WHEN** a model call terminates with a transport, rate-limit, or provider-service exception after SDK handling
- **THEN** the application output retry does not invoke the model again for that failure
- **AND** the durable source outcome retains its run-level retryability decision

### Requirement: Partial draft run retryability reflects failed outcomes
The system SHALL persist terminal analysis-run retryability from all failed source outcomes regardless of whether the run produced a valid partial aggregate.

#### Scenario: Partial run contains retryable source failure
- **WHEN** at least one source succeeds and at least one failed source outcome is retryable
- **THEN** the run becomes `PARTIAL`
- **AND** the run-level retryable field is true

#### Scenario: Partial run contains only permanent failures
- **WHEN** at least one source succeeds and every failed source outcome is non-retryable
- **THEN** the run becomes `PARTIAL`
- **AND** the run-level retryable field is false

#### Scenario: Partial run is retried
- **WHEN** a client retries a partial run without changing its reuse-key inputs
- **THEN** successful source outcomes are reused without another model call
- **AND** unresolved eligible sources are executed in the new run

