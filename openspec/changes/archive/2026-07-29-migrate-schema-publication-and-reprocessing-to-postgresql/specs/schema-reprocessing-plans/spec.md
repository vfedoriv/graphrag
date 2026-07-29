## ADDED Requirements

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
