# document-storage-reconciliation Specification

## Purpose
Define durable recovery and operational visibility for document metadata and binary-storage mutations.

## Requirements
### Requirement: Document storage mutations are recoverable
The system SHALL persist an idempotent storage mutation record for document binary store, replacement, and deletion operations until the external storage state and document metadata state are reconciled.

#### Scenario: Metadata persistence fails after binary store
- **WHEN** binary storage succeeds but the corresponding document metadata transaction does not commit
- **THEN** the system records an unfinished storage mutation
- **AND** reconciliation removes or repairs the unreferenced binary without altering unrelated documents

#### Scenario: Binary delete fails after deletion intent
- **WHEN** a document deletion requires a binary delete that fails
- **THEN** the document is not reported as successfully deleted
- **AND** reconciliation retains enough operation data to retry the binary cleanup safely

### Requirement: Storage reconciliation is observable
The system SHALL emit metrics and structured logs for pending, completed, retried, and permanently failed storage mutations without logging document content.

#### Scenario: Reconciler processes pending work
- **WHEN** the reconciler processes an unfinished storage mutation
- **THEN** it records the outcome and operation identifier
- **AND** it exposes the outcome through operational telemetry

### Requirement: Storage mutation journals survive target lifecycle changes
The system SHALL store document filesystem mutation journals in PostgreSQL without cascading deletion from their target documents and SHALL retry them idempotently.

#### Scenario: A target document record is deleted
- **WHEN** an outstanding delete mutation still requires filesystem cleanup
- **THEN** the journal remains available until reconciliation reaches a terminal success

#### Scenario: Reconciliation repeats completed external work
- **WHEN** the binary has already reached the intended state
- **THEN** reconciliation treats the external step as successful and completes the journal
