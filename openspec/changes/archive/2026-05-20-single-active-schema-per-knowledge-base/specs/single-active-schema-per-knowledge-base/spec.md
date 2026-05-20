## ADDED Requirements

### Requirement: Schema activation is exclusive within a knowledge base
The system MUST ensure that at most one schema is active for a given knowledge base at any time.

#### Scenario: Activating a schema deactivates siblings
- **WHEN** a schema in a knowledge base is activated
- **THEN** that schema MUST be marked active
- **AND** all other schemas in the same knowledge base MUST be marked inactive

#### Scenario: Activation does not affect other knowledge bases
- **WHEN** a schema in knowledge base A is activated
- **THEN** schema active states in knowledge base B MUST remain unchanged

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
