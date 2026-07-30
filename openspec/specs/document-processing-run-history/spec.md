# document-processing-run-history Specification

## Purpose
TBD - created by archiving change add-document-processing-options. Update Purpose after archive.
## Requirements
### Requirement: Processing runs are recorded
The system SHALL record each document processing attempt as a durable processing run covering parsing, chunking, embedding, and graph extraction orchestration.

#### Scenario: Processing run starts
- **WHEN** a valid document processing request starts
- **THEN** the system creates a processing run linked to the document
- **AND** the run records the document id, knowledge base id, source SHA-256 hash, parser identifier, detected file format, requested options, saved default options snapshot, effective options, started timestamp, and running status

#### Scenario: Processing run completes
- **WHEN** document processing completes successfully
- **THEN** the processing run records a completed status and completed timestamp
- **AND** the run is marked as the active completed processing run for the document

#### Scenario: Processing run fails
- **WHEN** parsing, chunking, embedding, or graph extraction fails after a processing run has started
- **THEN** the processing run records a failed status, completed timestamp, and error message
- **AND** the document status reflects the failed processing attempt

### Requirement: Processing run activation preserves the latest successful result
The system SHALL keep active completed processing run metadata aligned with the chunks and graph artifacts used for retrieval.

#### Scenario: First successful run becomes active
- **WHEN** a document is processed successfully for the first time
- **THEN** the completed processing run is marked active

#### Scenario: Successful overwrite creates new active run
- **WHEN** a document with an active completed processing run is processed successfully with overwrite enabled
- **THEN** the new completed processing run is marked active
- **AND** previous completed processing runs for that document are marked inactive

#### Scenario: Failed overwrite keeps previous active run
- **WHEN** a document with an active completed processing run is processed with overwrite enabled and the new attempt fails
- **THEN** the failed processing run is not marked active
- **AND** the previous completed processing run remains active

### Requirement: Effective processing options are auditable
The system SHALL retain enough option metadata on each processing run to reproduce which processing behavior was requested and applied.

#### Scenario: Client inspects processing run data
- **WHEN** processing run data is read by backend services or future API surfaces
- **THEN** requested options, saved default option snapshot, and effective options are available separately
- **AND** values are stored after registry validation and normalization

### Requirement: Processing stage transitions remain durable under orchestration extraction
The system SHALL preserve processing-run stage, status, completion, error, and active-completed semantics while document processing is decomposed into workflow stages.

#### Scenario: Stage-oriented processing succeeds
- **WHEN** all document processing stages complete successfully
- **THEN** the processing run is completed and active according to the existing processing-run contract

#### Scenario: Stage-oriented processing fails
- **WHEN** one processing stage fails after a run starts
- **THEN** the processing run is failed with its stage and error recorded
- **AND** a previously active completed processing run remains active when applicable

### Requirement: Processing and extraction run history is relational
The system SHALL persist assigned run IDs, document ownership, lifecycle state, retry metadata, timestamps, and optimistic versions in PostgreSQL.

#### Scenario: Processing starts and completes
- **WHEN** document processing is accepted
- **THEN** a `RUNNING` relational checkpoint commits before graph work
- **AND** a `COMPLETED` checkpoint commits only after required external work succeeds

#### Scenario: Processing is interrupted
- **WHEN** a run remains stale in a non-terminal state
- **THEN** recovery marks it failed or retryable according to existing policy
- **AND** its history remains queryable through the existing API

### Requirement: Completed processing uniqueness is enforced
The system SHALL prevent more than one active completed processing result for a document while preserving explicit overwrite behavior and historical run visibility.

#### Scenario: A successful document is processed without overwrite
- **WHEN** a completed active result already exists and overwrite is not authorized
- **THEN** processing is rejected without creating a second active completed result

### Requirement: Chunking policy snapshot
Each document processing run SHALL snapshot the effective strategy name/revision, canonical chunk-settings hash, tokenizer or estimator identity, exact-versus-conservative count mode, and effective chunker revision used by that attempt.

#### Scenario: Processing starts
- **WHEN** a processing run begins
- **THEN** its chunking snapshot is fixed for the attempt and remains queryable after success or failure

#### Scenario: Runtime setting changes during processing
- **WHEN** a live chunking setting changes after the run snapshot is created
- **THEN** the in-flight run continues with its snapshot rather than mixing revisions
