# schema-draft-publication Specification

## Purpose
TBD - created by archiving change add-schema-draft-validation-and-publication. Update Purpose after archive.
## Requirements
### Requirement: Publication readiness is explicit and revision-specific
The system SHALL report publication readiness for a specific current draft, aggregate, decision, and evaluation revision and SHALL identify every blocking reason without publishing a schema.

#### Scenario: Draft is ready for publication
- **WHEN** the effective projection passes normal schema registry validation, has no unresolved blocking conflicts or pending required guided candidates, targets an unused schema identity, and satisfies the configured current-evaluation policy
- **THEN** readiness is reported as ready with the exact revision and projection content hash

#### Scenario: Blocking conflicts remain
- **WHEN** a key, incompatible type, relationship direction, or pinned-definition conflict is unresolved
- **THEN** readiness is not ready
- **AND** every blocking conflict identifier is returned

#### Scenario: Guided candidate without evidence is pending
- **WHEN** a required guided candidate has no observed evidence and no explicit accept, modify, or reject decision
- **THEN** readiness is not ready
- **AND** identifies the required pending decision

#### Scenario: Evaluation is stale or incomplete
- **WHEN** the publication policy requires evaluation and the latest qualifying evaluation does not match the current draft revision or lacks the required successful-document threshold
- **THEN** readiness is not ready
- **AND** reports the evaluation precondition that is missing

### Requirement: Publishing creates a normal inactive editable schema
The system SHALL publish a ready draft by registering its exact effective projection through the existing schema registry, associating the schema with the draft knowledge base, and retaining the resulting schema as an ordinary inactive schema governed by existing mutation rules.

#### Scenario: Ready draft is published
- **WHEN** a client publishes using the exact ready draft revision and projection content hash
- **THEN** the system validates and creates one inactive generated schema with the draft target name and version
- **AND** associates it with the draft knowledge base
- **AND** records the schema identifier and publication content hash on the draft
- **AND** marks the draft published and read-only

#### Scenario: Published inactive schema is edited
- **WHEN** a client updates the published schema while it is not active for any knowledge base and preserves its name and version
- **THEN** existing inactive-schema update behavior applies
- **AND** the published draft remains an immutable audit snapshot of what it originally published
- **AND** retrieval can identify that the current schema content hash differs from the publication content hash

#### Scenario: Published schema is activated
- **WHEN** a client explicitly calls the existing schema activation operation after publication
- **THEN** normal single-active-schema behavior applies
- **AND** publication itself is not repeated

#### Scenario: Publish is requested with a stale draft revision
- **WHEN** the supplied draft revision or content hash no longer matches the ready projection
- **THEN** publication is rejected as a conflict
- **AND** no schema is created

### Requirement: Publication is atomic and idempotent
The system SHALL prevent duplicate schema creation under retries or concurrent publication attempts and SHALL leave the draft and schema registry consistent if publication fails.

#### Scenario: Identical publish request is retried
- **WHEN** a published draft receives the same publication request again
- **THEN** the system returns the existing publication result
- **AND** does not create another schema

#### Scenario: Target identity was claimed concurrently
- **WHEN** another schema with the target name and version is created before publication commits
- **THEN** publication fails as a conflict
- **AND** the draft remains open and linked to no partially created schema

#### Scenario: Registry validation fails during publication
- **WHEN** normal schema registry validation rejects the projection
- **THEN** publication fails with validation details
- **AND** the draft remains open with its evidence and decisions unchanged

#### Scenario: Failure occurs after schema creation begins
- **WHEN** publication cannot atomically complete the schema association and draft link
- **THEN** transaction handling or reconciliation prevents a duplicate or orphaned successful publication from being reported

### Requirement: Publication does not activate or reprocess automatically
The system SHALL keep publication, activation, and document reprocessing as separate explicit client operations.

#### Scenario: Draft publication succeeds
- **WHEN** a draft is published successfully
- **THEN** the knowledge base active schema remains unchanged
- **AND** no document processing request or reprocessing plan is started

### Requirement: Publication is revision-specific relational state
The system SHALL persist publication readiness, exact aggregate revision identity, resulting schema identity, lifecycle state, retries, and optimistic version in PostgreSQL.

#### Scenario: A ready revision is published
- **WHEN** the selected aggregate satisfies currentness, review, and evaluation readiness
- **THEN** exactly one inactive schema is created for the publication identity
- **AND** the relational publication reaches completed state

#### Scenario: Completion fails after schema creation
- **WHEN** the schema exists but publication completion was interrupted
- **THEN** recovery resolves the same schema idempotently
- **AND** does not create a duplicate publication
