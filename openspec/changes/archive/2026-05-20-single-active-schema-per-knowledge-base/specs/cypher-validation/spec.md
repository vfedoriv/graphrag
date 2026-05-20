## MODIFIED Requirements

### Requirement: Schema references are validated by Cypher context
The system SHALL validate node labels, relationship types, and properties against the active schema according to their Cypher syntactic context, where active schema resolution for the target knowledge base is unambiguous because only one schema can be active in that knowledge base.

#### Scenario: Valid node label is accepted
- **WHEN** a read query contains a node pattern with an allowed label
- **THEN** validation MUST NOT report that label as unknown

#### Scenario: Unknown node label is rejected
- **WHEN** a read query contains a node pattern with a label that is not allowed by the active schema or infrastructure labels
- **THEN** validation MUST report `Unknown label` for that label

#### Scenario: Relationship type is not treated as label
- **WHEN** a read query contains a relationship pattern with an allowed relationship type
- **THEN** validation MUST NOT validate that relationship type as a node label

#### Scenario: Single active schema is used for validation scope
- **WHEN** Cypher validation resolves the active schema for a knowledge base
- **THEN** validation MUST use exactly one active schema definition for that knowledge base
