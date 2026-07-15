# schema-draft-evaluation Specification

## Purpose
TBD - created by archiving change add-schema-draft-validation-and-publication. Update Purpose after archive.
## Requirements
### Requirement: Draft evaluation uses explicitly held-out documents
The system SHALL allow a client to start a durable evaluation of the current reviewed draft projection against selected documents owned by the draft knowledge base that did not contribute active evidence to that projection.

#### Scenario: Start a held-out evaluation
- **WHEN** a client selects eligible knowledge-base documents and starts evaluation for a current draft revision
- **THEN** the system snapshots the draft projection, decisions, document SHA-256 values, AI profile identifier and revision, prompt revision, and evaluation settings
- **AND** returns an accepted response with a durable evaluation run identifier and status location

#### Scenario: Discovery source is selected as held-out
- **WHEN** a selected document is an active source contributing evidence to the evaluated aggregate revision
- **THEN** the system rejects it as ineligible for held-out evaluation
- **AND** identifies the ineligible document without starting model calls

#### Scenario: Held-out document changes before evaluation
- **WHEN** a selected document's current SHA-256 differs from the evaluation snapshot before it is processed
- **THEN** that document receives a stale-source outcome
- **AND** replacement content is not evaluated under the prior snapshot

#### Scenario: Evaluation does not persist extracted graph data
- **WHEN** a held-out document is dry-extracted against the draft projection
- **THEN** the system does not persist document chunks, embeddings, extraction runs, nodes, or relationships in the knowledge-base graph

### Requirement: Evaluation reports deterministic quality metrics
The system SHALL calculate contractual metrics from validated dry-extraction results using documented formulas and SHALL distinguish those metrics from model-based advisory assessments.

#### Scenario: Dry extraction produces recognized and unknown labels
- **WHEN** a held-out extraction returns entity and relationship observations
- **THEN** the result reports recognized and unknown label counts
- **AND** computes recognized entity rate as recognized entity observations divided by recognized plus unknown entity observations when the denominator is non-zero

#### Scenario: Schema validation drops relationships
- **WHEN** a dry-extracted relationship is rejected by schema constraints
- **THEN** the evaluation reports its dropped-relationship count and reason classification

#### Scenario: Node keys and property values are evaluated
- **WHEN** recognized node observations are available
- **THEN** the evaluation reports key availability rate, property type conflict counts, and missing-required-property counts using deterministic calculations

#### Scenario: Draft support risks are evaluated
- **WHEN** the evaluated projection contains candidates supported by only one active source or guided candidates without observed evidence
- **THEN** the evaluation reports those counts independently of held-out extraction success

#### Scenario: Metric denominator is empty
- **WHEN** a rate cannot be calculated because its denominator is zero
- **THEN** the metric is reported as not applicable rather than as zero or one hundred percent

### Requirement: Intended-question and schema-noise judgments are advisory
The system SHALL label intended-question coverage, possible schema noise, and overly document-specific concept judgments as advisory model assessments with evidence and reproducibility metadata.

#### Scenario: Intended questions are configured
- **WHEN** guidance contains intended questions and advisory evaluation is enabled
- **THEN** the response reports per-question `SUPPORTED`, `PARTIALLY_SUPPORTED`, or `UNSUPPORTED` assessments with concise schema-coordinate reasons
- **AND** the assessments are not represented as deterministic validation errors

#### Scenario: Advisory model assessment fails
- **WHEN** deterministic dry-extraction metrics complete but advisory assessment fails
- **THEN** the evaluation may complete with advisory warnings
- **AND** deterministic metrics remain available

### Requirement: Evaluation progress and results are durable
The system SHALL persist per-document evaluation outcomes, aggregate metrics, timestamps, and privacy-safe failures, and SHALL support retry by reusing matching successful outcomes.

#### Scenario: Some held-out documents fail
- **WHEN** at least one held-out document succeeds and at least one fails
- **THEN** the evaluation run becomes `PARTIAL`
- **AND** aggregate metrics identify the evaluated and failed document counts

#### Scenario: Evaluation is retried
- **WHEN** a client retries a partial or interrupted evaluation with an identical snapshot
- **THEN** successful matching document outcomes are reused
- **AND** only unresolved documents are scheduled

#### Scenario: Draft changes after evaluation
- **WHEN** guidance, decisions, or the effective projection changes after an evaluation completes
- **THEN** the prior evaluation remains auditable
- **AND** it is not current evidence for publication readiness of the new draft revision

### Requirement: Evaluation preserves AI profile and privacy controls
The system SHALL use the knowledge base active AI profile captured at evaluation start and SHALL observe dry-extraction and advisory model calls through existing privacy-controlled AI observation behavior.

#### Scenario: Evaluation is logged
- **WHEN** an evaluation runs
- **THEN** normal logs contain only identifiers, fingerprints, counts, statuses, timings, metric summaries, and exception classes
- **AND** do not contain document text, prompts, responses, extracted payloads, or draft schema content

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
