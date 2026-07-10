## MODIFIED Requirements

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
