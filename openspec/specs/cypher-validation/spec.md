# cypher-validation Specification

## Purpose
Define the expected validation behavior for schema-constrained Cypher labels, relationship types, and property references before query execution.

## Requirements
### Requirement: Schema references are validated by Cypher context
The system SHALL validate node labels, relationship types, and properties against the active schema according to their Cypher syntactic context.

#### Scenario: Valid node label is accepted
- **WHEN** a read query contains a node pattern with an allowed label
- **THEN** validation MUST NOT report that label as unknown

#### Scenario: Unknown node label is rejected
- **WHEN** a read query contains a node pattern with a label that is not allowed by the active schema or infrastructure labels
- **THEN** validation MUST report `Unknown label` for that label

#### Scenario: Relationship type is not treated as label
- **WHEN** a read query contains a relationship pattern with an allowed relationship type
- **THEN** validation MUST NOT validate that relationship type as a node label

### Requirement: Relationship unions are validated completely
The system SHALL validate each relationship type named in a Cypher relationship union.

#### Scenario: Valid relationship union is accepted
- **WHEN** a read query contains a relationship pattern such as `[:TYPE_A|TYPE_B]` and both types are allowed by the active schema
- **THEN** validation MUST NOT report either relationship type as unknown

#### Scenario: Unknown relationship union member is rejected
- **WHEN** a read query contains a relationship pattern with at least one union member that is not allowed by the active schema
- **THEN** validation MUST report `Unknown relationship type` for each unknown relationship type

#### Scenario: Aliased relationship union is accepted
- **WHEN** a read query contains an aliased relationship pattern such as `[r:TYPE_A|TYPE_B]` and both types are allowed by the active schema
- **THEN** validation MUST NOT report relationship union members as labels or unknown relationship types

### Requirement: Property references remain schema constrained
The system SHALL reject simple qualified property references that are not allowed by the active schema or built-in infrastructure property allow-list.

#### Scenario: Valid qualified property is accepted
- **WHEN** a read query returns an allowed property through a qualified reference such as `n.title`
- **THEN** validation MUST NOT report that property as unknown

#### Scenario: Unknown qualified property is rejected
- **WHEN** a read query uses a qualified property reference that is not allowed by the active schema or built-in infrastructure property allow-list
- **THEN** validation MUST report `Unknown property` for that property
