# single-active-schema-per-knowledge-base Specification

## Purpose
TBD - created by archiving change single-active-schema-per-knowledge-base. Update Purpose after archive.
## Requirements
### Requirement: Schema activation is exclusive within a knowledge base
The system MUST ensure that at most one schema is active for a given knowledge base at any time. When activation provisions a missing knowledge base, it MUST use the common knowledge-base lifecycle before setting active-schema state.

#### Scenario: Activating a schema deactivates siblings
- **WHEN** a schema in a knowledge base is activated
- **THEN** that schema MUST be marked active
- **AND** all other schemas in the same knowledge base MUST be marked inactive

#### Scenario: Activation does not affect other knowledge bases
- **WHEN** a schema in knowledge base A is activated
- **THEN** schema active states in knowledge base B MUST remain unchanged

#### Scenario: Activation provisions missing knowledge base consistently
- **WHEN** schema activation targets a missing knowledge base
- **THEN** the system provisions the knowledge base with required defaults before applying exclusive activation

### Requirement: Activating an already active schema is idempotent
The system MUST treat activation of an already active schema as a successful no-op.

#### Scenario: Repeated activation on same schema
- **WHEN** the requested schema is already active in its knowledge base
- **THEN** the activation request MUST succeed
- **AND** no additional schema in that knowledge base becomes active

### Requirement: Activation transitions are atomic per request
The system MUST apply activation and sibling deactivation as one consistent state transition.

#### Scenario: Activation succeeds with consistent final state
- **WHEN** an activation request completes successfully
- **THEN** exactly one schema in that knowledge base MUST be active

#### Scenario: Activation fails before completion
- **WHEN** activation cannot complete successfully
- **THEN** the system MUST NOT persist a partially applied state that leaves multiple active schemas in the same knowledge base

### Requirement: Active schema cannot be deleted
The system MUST reject deletion of a schema while that schema is active for any knowledge base.

#### Scenario: Active schema delete is rejected
- **WHEN** a client deletes a schema id that is the active schema for a knowledge base
- **THEN** the system rejects the request as a conflict
- **AND** the knowledge base continues to reference the same active schema
- **AND** the active schema remains available for retrieval
