## ADDED Requirements

### Requirement: Evaluation result values have explicit metric contracts
The system SHALL return aggregate and per-document evaluation metrics through typed rate and count contracts containing a stable metric identifier, evidence coordinates, and explicit applicability rather than generic object values.

#### Scenario: Rate metric has observations
- **WHEN** a metric denominator is greater than zero
- **THEN** its response contains the metric identifier, numerator, denominator, calculated value, `APPLICABLE` state, and any evidence coordinates

#### Scenario: Rate metric has no observations
- **WHEN** a metric denominator is zero
- **THEN** its response contains a null calculated value and `NOT_APPLICABLE` state
- **AND** a frontend is not required to interpret the null as zero

#### Scenario: Count metric is reported
- **WHEN** evaluation calculates a contractual count such as a property conflict or support risk
- **THEN** the response contains its stable metric identifier, count, and relevant evidence coordinates

### Requirement: Advisory results have explicit status, reasons, and reproducibility
The system SHALL return advisory execution and question-coverage states through documented enums and SHALL include reasons, schema coordinates, warnings, and profile/prompt/contract reproducibility metadata.

#### Scenario: Intended question is assessed
- **WHEN** advisory evaluation completes for an intended question
- **THEN** the response identifies the question by fingerprint and reports `SUPPORTED`, `PARTIALLY_SUPPORTED`, or `UNSUPPORTED`
- **AND** it includes zero or more reasons and supporting schema coordinates

#### Scenario: Advisory assessment is disabled or fails
- **WHEN** advisory evaluation is disabled or fails while deterministic evaluation remains available
- **THEN** the response reports an explicit advisory execution state and warning
- **AND** deterministic metrics remain typed and readable

#### Scenario: Historical version-one result is read
- **WHEN** a client reads a durable evaluation created under `schema-draft-evaluation-v1`
- **THEN** the system maps supported values into the typed response contract
- **AND** preserves the original contract revision and uses empty collections for details not persisted by version one

### Requirement: Evaluation outcomes use the standard page envelope
The system SHALL represent the selected per-document outcome slice in an evaluation status response as a typed page envelope with zero-based page number, bounded page size, total element count, and deterministic content order.

#### Scenario: Client polls a paged evaluation status
- **WHEN** a client requests an evaluation run with page and size parameters
- **THEN** the response includes the bounded outcome page as `page`, `size`, `totalElements`, and `content`
- **AND** aggregate evaluation counts and metrics describe the entire run
