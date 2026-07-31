# privacy-safe-operational-logging Specification

## Purpose
Defines privacy and operational-safety requirements for application and test logging.
## Requirements
### Requirement: Normal operational logs are content-safe
The system SHALL NOT emit document text, prompts, user query text, model responses, generated schemas, or extracted graph payloads at normal application log levels.

#### Scenario: AI workflow processes content
- **WHEN** document processing, graph extraction, schema generation, Cypher generation, or hybrid search emits normal operational logs
- **THEN** the logs include safe identifiers, lengths, counts, timings, statuses, and error classification as applicable
- **AND** the logs do not include the input or output content

#### Scenario: Workflow fails
- **WHEN** a content-bearing workflow fails
- **THEN** the error log includes a correlation identifier, workflow stage, exception classification, and sanitized diagnostic message
- **AND** it does not include raw document or model content

### Requirement: Content diagnostics are explicit and non-production
The system SHALL permit any local diagnostic content output only through an explicit non-production configuration with bounded capture and an auditable enablement signal.

#### Scenario: Production-equivalent profile is active
- **WHEN** a standard runtime profile is active without explicit local diagnostic configuration
- **THEN** application logs do not emit content previews at any enabled normal diagnostic level

### Requirement: Test logging is concise and actionable
The system SHALL use test-only logging configuration that suppresses routine framework and payload noise while retaining application warnings/errors and test failures.

#### Scenario: Full test suite runs
- **WHEN** `./mvnw test` executes
- **THEN** routine Testcontainers, Neo4j expected-schema, and payload logs are reduced to configured actionable levels
- **AND** a failing application test still exposes its failure and relevant application diagnostics

### Requirement: Model failure diagnostics are bounded and content-safe
The system SHALL make source-model failures operationally diagnosable using bounded structured metadata while treating raw exception and response content as sensitive.

#### Scenario: Retried model output is logged
- **WHEN** a candidate output attempt is retried because its normal assistant output is unusable
- **THEN** the warning identifies the draft/run/source/chunk context when applicable, logical output attempt, elapsed time, broad category, detailed code, response length/fingerprint, finish reason, and token counts when available
- **AND** it does not identify the event as an internal SDK HTTP attempt

#### Scenario: Terminal model failure is logged
- **WHEN** a source model call terminates unsuccessfully
- **THEN** the warning includes a bounded outer-to-root exception type chain, root exception type, provider status when available, configured timeout/retry metadata, and a non-reversible message fingerprint
- **AND** it excludes raw exception messages, headers, provider bodies, prompts, source text, normal or reasoning output, candidate payloads, and credentials

#### Scenario: Logging and trace capture use separate content policies
- **WHEN** AI observation content capture is enabled
- **THEN** normal application logs remain metadata-only
- **AND** any captured prompt or response content continues to flow only through `AiObservationService` according to its runtime privacy and length controls

### Requirement: Content-free expansion diagnostics
Operational logs SHALL record expansion counts, budget decisions, strategy revisions, validation outcomes, and timings without logging child or parent text, embeddings, prompts, queries, or model responses.

#### Scenario: Parent rejected during expansion
- **WHEN** a candidate parent fails scope validation
- **THEN** logs contain safe identifiers or fingerprints and the rejection reason but no source content
