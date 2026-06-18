## ADDED Requirements

### Requirement: Runtime settings are exposed through an allowlisted API
The system SHALL expose mutable application settings only through an explicit allowlist of backend-owned settings.

#### Scenario: Client lists runtime settings
- **WHEN** a client requests the runtime settings list
- **THEN** the response includes each allowlisted setting with key, category, value type, current value, default value, source, mutability, live-apply status, sensitivity, and validation constraints
- **AND** non-allowlisted Spring, system, management, Neo4j, storage, and multipart properties are not exposed as mutable settings

#### Scenario: Client requests a non-allowlisted setting update
- **WHEN** a client submits an update for a setting key that is not in the allowlist
- **THEN** the system rejects the update
- **AND** no persisted runtime setting is changed

### Requirement: Runtime setting updates are validated and persisted
The system SHALL validate submitted runtime setting values before persisting them in Neo4j.

#### Scenario: Valid setting update is submitted
- **WHEN** a client updates an allowlisted runtime setting with a valid value
- **THEN** the system persists the override in Neo4j
- **AND** subsequent setting reads return the persisted value as the current value

#### Scenario: Invalid setting update is submitted
- **WHEN** a client updates an allowlisted runtime setting with a value that violates type, range, or collection constraints
- **THEN** the system rejects the update with a validation error
- **AND** the previous setting value remains active

### Requirement: Safe runtime settings apply without restart
The system SHALL apply allowlisted live settings to subsequent workflow executions without requiring an application restart.

#### Scenario: Query limit setting changes
- **WHEN** a client changes a live query safety setting such as max rows, require limit, blocked keywords, or hybrid search bounds
- **THEN** subsequent query validation, generation, execution, or hybrid search requests use the updated setting

#### Scenario: Extraction or chunking setting changes
- **WHEN** a client changes a live extraction payload limit or chunking setting
- **THEN** subsequent document processing and graph extraction work uses the updated setting
- **AND** already processed documents are not retroactively reprocessed

#### Scenario: Startup-bound setting is represented
- **WHEN** a setting is startup-bound or unsupported for live application
- **THEN** the settings API reports it as not live-applied if it is exposed for visibility
- **AND** updating live-only behavior does not attempt to rebuild unrelated infrastructure

### Requirement: Runtime settings fall back to startup defaults
The system SHALL use configured startup properties as defaults when no persisted runtime override exists.

#### Scenario: No override exists
- **WHEN** the application resolves an allowlisted setting that has no persisted override
- **THEN** it uses the value bound from startup configuration

#### Scenario: Override is cleared
- **WHEN** a client clears a persisted override for an allowlisted setting
- **THEN** the system falls back to the startup default for that setting
