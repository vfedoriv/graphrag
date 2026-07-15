## ADDED Requirements

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
