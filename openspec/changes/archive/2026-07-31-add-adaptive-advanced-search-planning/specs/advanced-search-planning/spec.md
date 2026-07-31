## ADDED Requirements

### Requirement: Planning is structured and bounded
The system SHALL use the snapshotted active profile and schema to produce at most the configured number of normalized subquestions, exact identifiers or phrases, metadata constraints, and typed graph requests.

#### Scenario: Planner returns executable Cypher
- **WHEN** model output contains Cypher or an unsupported tool instruction
- **THEN** the system rejects that content and does not execute it

### Requirement: Every plan is validated before retrieval
The system SHALL validate plan counts, string bounds, metadata fields, and all graph identifiers, operators, literals, projections, and limits against run snapshots and typed policies.

#### Scenario: Planner names a stale schema property
- **WHEN** a property is absent from the run's snapshotted schema
- **THEN** the graph request is rejected before branch execution

### Requirement: Planning has a deterministic fallback
The system SHALL use the original query as one bounded text subquery when structured planning fails and sufficient deadline remains.

#### Scenario: Planning response is malformed
- **WHEN** structured output cannot be parsed or repaired within the planning stage
- **THEN** retrieval continues with the deterministic fallback and diagnostics record fallback use

### Requirement: Sufficiency evaluation is evidence grounded
The system SHALL evaluate ranked evidence for subquestion coverage, contradictions, and concrete missing evidence using a structured bounded result.

#### Scenario: All subquestions have non-contradictory evidence
- **WHEN** the evaluator reports complete coverage and no concrete gap
- **THEN** the workflow skips follow-up retrieval

### Requirement: Follow-up retrieval is strictly bounded
The system SHALL permit at most one follow-up round with at most two validated refined queries only when a concrete gap exists, cancellation is not requested, and the reserved deadline remains.

#### Scenario: Concrete gap exists but deadline is nearly exhausted
- **WHEN** remaining time is below the configured follow-up threshold
- **THEN** no follow-up branch starts and the limitation is retained for synthesis
