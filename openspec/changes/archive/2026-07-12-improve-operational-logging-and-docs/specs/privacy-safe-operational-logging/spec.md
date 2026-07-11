## ADDED Requirements

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
