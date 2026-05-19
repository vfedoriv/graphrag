## ADDED Requirements

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
