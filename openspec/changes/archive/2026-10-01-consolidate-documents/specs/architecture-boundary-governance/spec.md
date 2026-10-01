## ADDED Requirements

### Requirement: Consolidated documents has enforceable internal ownership
Architecture verification SHALL treat document management, processing, extraction, run history and recovery, storage reconciliation, parsing/chunking, and document-owned relational and graph adapters as document internals. Foreign feature access SHALL use public capabilities and immutable boundary values, except for exact pre-existing dependencies explicitly frozen with a retirement roadmap step. Consolidation SHALL preserve all ownership restrictions established by reprocessing, knowledge-base lifecycle, and AI state isolation.

#### Scenario: A new foreign caller accesses document internals
- **WHEN** a production feature outside documents introduces direct access to document persistence records, repository ports, application implementations, or effect adapters
- **THEN** architecture verification fails and identifies the dependency
- **AND** an exception for another caller does not authorize the new access

#### Scenario: An established public capability is consumed
- **WHEN** an integration adapter accesses documents through the public reprocessing, preparation, outcome, lifecycle, or stored-embedding capability
- **THEN** architecture verification accepts the public capability and immutable value dependencies
- **AND** rejects bypasses to document internals

### Requirement: Deterministic document rules are isolated from external effects
Architecture verification SHALL reject persistence, filesystem, model-client, application-workflow, and assembly dependencies in deterministic document rules. Document workflows SHALL invoke external effects through owned ports or governed adapters rather than directly using external clients. Typed settings and observability access SHALL remain governed support dependencies.

#### Scenario: A pure chunk planning rule acquires a client
- **WHEN** a deterministic document rule depends on a database, filesystem, or model client
- **THEN** architecture verification rejects the dependency

#### Scenario: A workflow persists an embedding
- **WHEN** a document workflow invokes its governed persistence adapter or owned port
- **THEN** verification accepts that boundary
- **AND** rejects a direct external-client dependency in the workflow

### Requirement: Document compatibility callers use AI ownership
Architecture verification SHALL require document processing and migration preparation to invoke AI-owned embedding compatibility through non-secret boundary values. Documents SHALL NOT use the transitional compatibility bridge retained for unmigrated search callers or duplicate AI compatibility rules.

#### Scenario: A document compatibility check uses the legacy bridge
- **WHEN** verification finds document processing or preparation depending on the transitional compatibility bridge
- **THEN** verification fails

#### Scenario: Stored observations enter compatibility
- **WHEN** AI reads stored observations through its port and the documents public capability
- **THEN** verification preserves the existing observation-versus-rule ownership boundary

### Requirement: Document consolidation exceptions cannot expand
Architecture verification SHALL freeze transitional foreign callers and document dependencies on unmigrated schema, knowledge-base, AI, settings, or support implementations by exact originating class and target dependency, with an assigned retirement step. Exceptions SHALL NOT permit feature dependencies on assembly, secret-bearing public values, new callers, or removed reprocessing/knowledge-base/AI state bypasses.

#### Scenario: A deferred schema dependency remains
- **WHEN** an explicitly frozen document schema-resolution dependency remains pending registry migration
- **THEN** verification accepts only its recorded class/dependency pair
- **AND** identifies registry migration as its retirement step

#### Scenario: An exception grows
- **WHEN** a caller adds an unlisted target dependency or a different caller attempts the same access
- **THEN** verification fails
