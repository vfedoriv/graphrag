## ADDED Requirements

### Requirement: Runtime settings support atomic bulk updates
The system SHALL allow clients to update multiple allowlisted mutable runtime settings in one request and SHALL apply the submitted updates atomically.

#### Scenario: Valid bulk setting update is submitted
- **WHEN** a client submits a bulk update request containing multiple unique allowlisted mutable setting keys with valid values
- **THEN** the system persists each submitted override in Neo4j
- **AND** the response contains the updated runtime setting representations for the submitted keys in request order
- **AND** subsequent setting reads return each persisted value as the current value

#### Scenario: Bulk update contains invalid setting
- **WHEN** a client submits a bulk update request where any setting key is not allowlisted, not mutable, or has a value that violates type, range, or collection constraints
- **THEN** the system rejects the request with a validation error
- **AND** no submitted runtime setting override is changed

#### Scenario: Bulk update contains duplicate keys
- **WHEN** a client submits a bulk update request with the same setting key more than once
- **THEN** the system rejects the request with a validation error
- **AND** no submitted runtime setting override is changed

#### Scenario: Bulk update is empty
- **WHEN** a client submits a bulk update request with no setting updates
- **THEN** the system rejects the request with a validation error
- **AND** no persisted runtime setting override is changed
