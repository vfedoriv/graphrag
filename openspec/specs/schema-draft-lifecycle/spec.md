# schema-draft-lifecycle Specification

## Purpose
TBD - created by archiving change add-persistent-schema-drafts. Update Purpose after archive.
## Requirements
### Requirement: Schema drafts are managed knowledge-base resources
The system SHALL allow multiple schema drafts to be created for a managed knowledge base, with each draft recording a target schema name and version, optional base schema, guidance snapshot, lifecycle status, revision, creator-independent timestamps, and active AI profile context.

#### Scenario: Create an initial discovery draft
- **WHEN** a client creates a draft with a valid target name and version and no base schema
- **THEN** the system creates an `OPEN` draft owned by the target knowledge base
- **AND** the draft is not registered or usable as an active extraction schema

#### Scenario: Create an evolution draft
- **WHEN** a client creates a draft with a base schema associated with the same knowledge base
- **THEN** the base schema name matches the target name
- **AND** the target version is greater than the base schema version
- **AND** inherited schema elements are available to draft review and diff generation

#### Scenario: Base schema is not associated with the knowledge base
- **WHEN** a client supplies a missing schema or a schema not associated with the target knowledge base as the base
- **THEN** the system rejects draft creation using the established not-found behavior

#### Scenario: Multiple drafts target one schema identity
- **WHEN** more than one open draft targets the same schema name and version
- **THEN** the system permits the drafts to coexist
- **AND** target identity uniqueness is enforced only when a draft is published

### Requirement: Draft mutations use optimistic revisions
The system SHALL require revision-aware mutation of draft metadata, guidance, sources, and decisions so concurrent clients cannot silently overwrite each other.

#### Scenario: Client updates current draft revision
- **WHEN** a client updates an open draft using its current revision
- **THEN** the mutation succeeds atomically
- **AND** the draft revision and updated timestamp advance

#### Scenario: Client updates a stale draft revision
- **WHEN** a client submits a mutation using an older draft revision
- **THEN** the system rejects the mutation as a conflict
- **AND** current draft state remains unchanged

#### Scenario: Guidance changes
- **WHEN** structured guidance or free-form instructions change successfully
- **THEN** the system records a new guidance revision and content fingerprint
- **AND** prior analysis runs remain auditable but are not treated as current for the new guidance

### Requirement: Published drafts become read-only audit records
The system SHALL keep an open draft editable until publication and SHALL retain a published draft as a read-only audit record linked to its registered schema.

#### Scenario: Open draft is edited
- **WHEN** a client mutates metadata, guidance, sources, or decisions on an `OPEN` draft using the current revision
- **THEN** the system accepts the mutation subject to field validation and analysis-run concurrency rules

#### Scenario: Published draft mutation is attempted
- **WHEN** a client attempts to mutate a draft after it has been published
- **THEN** the system rejects the draft mutation as a conflict
- **AND** the published registered schema remains independently editable while inactive under existing schema rules

#### Scenario: Draft is retrieved after publication
- **WHEN** a client retrieves a published draft
- **THEN** the response includes its final aggregate revision, publication schema identifier, publication content hash, evidence, decisions, and run history

### Requirement: Draft deletion preserves ownership and audit constraints
The system SHALL permit explicit deletion of an open draft only when no analysis mutation is running, and SHALL NOT delete published draft audit records through the open-draft deletion operation.

#### Scenario: Delete an unused open draft
- **WHEN** a client deletes an open draft with no running analysis
- **THEN** the system deletes draft metadata and all draft-owned source artifacts
- **AND** it does not delete referenced knowledge-base documents or registered schemas

#### Scenario: Delete while analysis is running
- **WHEN** a client attempts to delete a draft with a running analysis
- **THEN** the system rejects deletion as a conflict
- **AND** analysis state and source artifacts remain available

#### Scenario: Delete a published draft
- **WHEN** a client attempts to delete a published draft through the draft deletion operation
- **THEN** the system rejects deletion to preserve the publication audit trail

### Requirement: Draft operations preserve privacy-safe logging
The system SHALL keep draft operational logs metadata-first and SHALL apply existing AI observation content-capture controls to background source analysis.

#### Scenario: Draft source or decision is mutated
- **WHEN** a draft mutation succeeds or fails
- **THEN** normal logs include only identifiers, fingerprints, counts, revision numbers, statuses, timings, and exception classes
- **AND** normal logs exclude source text, guidance content, candidate payloads, schema projections, and model responses
