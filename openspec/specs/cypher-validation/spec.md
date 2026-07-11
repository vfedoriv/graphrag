# cypher-validation Specification

## Purpose
Define the expected validation behavior for schema-constrained Cypher labels, relationship types, and property references before query execution.
## Requirements
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

### Requirement: Validation rejections return explicit error details
The system SHALL return validation error messages collected during Cypher validation in the query execution rejection response payload.

#### Scenario: Single validation error is returned in response
- **WHEN** query execution is rejected because validation reports one error
- **THEN** the response includes `Query validation failed` as the summary message
- **AND** the response includes the validation error text from the validation step

#### Scenario: Multiple validation errors are returned in response
- **WHEN** query execution is rejected because validation reports multiple errors
- **THEN** the response includes all validation error texts from the validation step
- **AND** the response preserves message order from the validation result

### Requirement: Validation rejection logs include actionable reason summary
The system SHALL log validation rejection with a sanitized summary of validation error messages.

#### Scenario: Validation rejection is logged with detailed reason
- **WHEN** query execution is rejected by validation
- **THEN** logs include knowledge base id and validation error count
- **AND** logs include validation error message text summary sufficient to explain the rejection reason

#### Scenario: Validation errors include user-provided values
- **WHEN** a validation error message references user-provided query text or parameter values
- **THEN** log output remains sanitized according to existing log sanitization rules

### Requirement: Explicit limits are validated against effective runtime policy
The system SHALL validate executable top-level Cypher limits, including bound limit parameters, against the effective runtime maximum rows setting.

#### Scenario: Explicit limit is within policy
- **WHEN** a validated query has an explicit top-level limit less than or equal to the effective runtime maximum
- **THEN** validation preserves that limit
- **AND** the validation response reports the effective maximum and timeout policy

#### Scenario: Limit text occurs in a literal or comment
- **WHEN** text resembling a `LIMIT` clause occurs only inside a Cypher literal or comment
- **THEN** validation does not treat that text as an executable limit

### Requirement: Validation reports the applied runtime query policy
The system SHALL return the runtime query policy snapshot used for validation in validation, generation, ask, and execution responses.

#### Scenario: Live settings override startup defaults
- **WHEN** a client updates a live query maximum or timeout setting before a query request
- **THEN** every query response for that request reports the overridden effective value
- **AND** validation and execution use the same effective value

