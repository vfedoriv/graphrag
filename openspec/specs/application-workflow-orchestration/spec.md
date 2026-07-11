# application-workflow-orchestration Specification

## Purpose
TBD - created by archiving change decompose-processing-orchestration. Update Purpose after archive.
## Requirements
### Requirement: Document processing uses explicit workflow stages
The system SHALL coordinate document processing through explicit stages for option resolution, parsing, chunk preparation, embedding persistence, graph extraction, and processing-run lifecycle updates.

#### Scenario: Processing succeeds
- **WHEN** a valid document processing request completes
- **THEN** the workflow executes the stages in dependency order
- **AND** the public processing result, document status, persisted chunks, graph artifacts, and observations remain compatible with the prior contract

#### Scenario: Processing stage fails
- **WHEN** parsing, chunk preparation, embedding persistence, or graph extraction fails after a processing run starts
- **THEN** the workflow records the failed stage and error through the run lifecycle component
- **AND** later stages do not execute

### Requirement: Runtime settings composition is typed and cohesive
The system SHALL compose runtime settings from a typed catalog, typed value codecs, override persistence, and named live appliers without feature services performing ad hoc string-key configuration reads.

#### Scenario: Feature resolves settings
- **WHEN** a workflow resolves query, chunking, extraction, or observability settings
- **THEN** it receives a typed settings value from a runtime settings accessor
- **AND** it does not parse raw persisted strings itself

#### Scenario: Live setting is cleared
- **WHEN** a mutable live setting with a direct live applier is cleared
- **THEN** the runtime behavior is reapplied to the startup default
- **AND** the settings response reflects the actual effective state

### Requirement: Schema generation collaborators are independently testable
The system SHALL separate schema-generation prompt construction, model invocation, graph-document mapping, and warning generation into cohesive collaborators.

#### Scenario: Model response is mapped
- **WHEN** schema generation receives a graph document from the model adapter
- **THEN** deterministic schema normalization and warning generation run without model-client or database side effects

