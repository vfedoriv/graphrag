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

### Requirement: Draft guidance is readable and typed
The system SHALL accept, validate, persist, and return draft guidance through a typed contract containing optional free-form additional instructions and structured discovery guidance, and SHALL return the value with its revision and fingerprint in draft responses.

#### Scenario: Client creates a draft with typed guidance
- **WHEN** a client creates a draft with valid additional instructions and structured discovery guidance
- **THEN** the system persists a canonical guidance value
- **AND** the response returns that value, guidance revision, and guidance fingerprint

#### Scenario: Client reopens an existing draft
- **WHEN** a client retrieves or lists a draft with saved guidance
- **THEN** the response contains the complete typed guidance value
- **AND** the client does not need to submit an empty replacement to discover the current draft state

#### Scenario: Client submits invalid guidance
- **WHEN** a create or guidance-update request violates the structured guidance constraints or contains unsupported fields
- **THEN** the system rejects the request using the established validation error format
- **AND** the draft revision and saved guidance remain unchanged

#### Scenario: Legacy guidance is read
- **WHEN** a draft contains a guidance shape written by the existing direct or wrapped guidance formats
- **THEN** the system normalizes it into the typed response contract without losing supported values
- **AND** a later successful update stores the canonical typed shape

### Requirement: Draft responses expose lightweight workflow summaries
The system SHALL include nullable current-analysis, latest-evaluation, and latest-reprocessing references in draft detail and list responses without embedding run or plan outcome collections.

#### Scenario: Draft has recoverable workflow state
- **WHEN** a client retrieves or lists a draft with analysis, evaluation, or reprocessing history
- **THEN** the response identifies the applicable current analysis and the latest evaluation and reprocessing resources with their statuses
- **AND** each reference indicates its current or latest semantics explicitly

#### Scenario: Draft has no workflow history
- **WHEN** a draft has no analysis, evaluation, or reprocessing resources
- **THEN** the corresponding workflow references are null

#### Scenario: Latest evaluation became stale
- **WHEN** a draft changes after its latest evaluation was created
- **THEN** the draft response may still reference that latest evaluation
- **AND** the reference reports that the evaluation is not current for the draft

#### Scenario: Knowledge base draft list is read
- **WHEN** a client lists multiple drafts
- **THEN** the system resolves their lightweight workflow references with bounded batch access
- **AND** does not load full source outcomes, evaluation outcomes, or plan items

### Requirement: Draft identity and lifecycle are relational
The system SHALL persist draft ownership, status, current revision references, running analysis claim, timestamps, and optimistic version in PostgreSQL.

#### Scenario: A draft is created
- **WHEN** a valid knowledge-base-scoped draft is requested
- **THEN** its assigned ID and ownership commit relationally
- **AND** the existing API representation is preserved

#### Scenario: A stale draft mutation occurs
- **WHEN** a caller mutates a draft using stale state
- **THEN** optimistic concurrency rejects the mutation

### Requirement: Draft navigation remains bounded during staged migration
The system SHALL produce ownership-safe draft summaries and detail navigation through bounded relational projections and SHALL represent not-yet-migrated downstream workflow summaries consistently.

#### Scenario: Drafts are listed
- **WHEN** a knowledge base contains drafts with analysis and review history
- **THEN** their current revisions and summary counts are loaded without per-draft repository queries
