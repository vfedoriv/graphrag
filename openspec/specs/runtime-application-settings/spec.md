# runtime-application-settings Specification

## Purpose
TBD - created by archiving change add-runtime-settings-ai-profiles. Update Purpose after archive.
## Requirements
### Requirement: Runtime settings are exposed through an allowlisted API
The system SHALL expose application settings only through an explicit allowlist of backend-owned settings, including mutable live settings and read-only configuration inventory entries.

#### Scenario: Client lists runtime settings
- **WHEN** a client requests the runtime settings list
- **THEN** the response includes each allowlisted setting with key, category, value type, current value, default value, source, mutability, live-apply status, sensitivity, validation constraints, update mode, and read-only or restart reason when applicable
- **AND** non-allowlisted Spring, system, management, Neo4j, storage, multipart, and application properties are not exposed as mutable settings

#### Scenario: Client requests a non-allowlisted setting update
- **WHEN** a client submits an update for a setting key that is not in the allowlist
- **THEN** the system rejects the update
- **AND** no persisted runtime setting is changed

### Requirement: Runtime setting updates are validated and persisted
The system SHALL validate submitted runtime setting values before persisting mutable runtime overrides in Neo4j.

#### Scenario: Valid setting update is submitted
- **WHEN** a client updates an allowlisted mutable runtime setting with a valid value
- **THEN** the system persists the override in Neo4j
- **AND** subsequent setting reads return the persisted value as the current value

#### Scenario: Invalid setting update is submitted
- **WHEN** a client updates an allowlisted mutable runtime setting with a value that violates type, range, or collection constraints
- **THEN** the system rejects the update with a validation error
- **AND** the previous setting value remains active

#### Scenario: Read-only setting update is submitted
- **WHEN** a client updates an allowlisted setting whose update mode is read-only, restart-required, profile-managed, or sensitive read-only
- **THEN** the system rejects the update with an error that explains why the setting cannot be changed through the runtime settings API
- **AND** no persisted runtime setting override is changed

### Requirement: Safe runtime settings apply without restart
The system SHALL apply allowlisted live settings to subsequent workflow executions without requiring an application restart and SHALL represent startup-bound settings without attempting unsafe live reconfiguration.

#### Scenario: Query limit setting changes
- **WHEN** a client changes a live query safety setting such as max rows, require limit, blocked keywords, or hybrid search bounds
- **THEN** subsequent query validation, generation, execution, or hybrid search requests use the updated setting

#### Scenario: Extraction or chunking setting changes
- **WHEN** a client changes a live extraction payload limit or chunking setting
- **THEN** subsequent document processing and graph extraction work uses the updated setting
- **AND** already processed documents are not retroactively reprocessed

#### Scenario: Startup-bound setting is represented
- **WHEN** a setting controls startup-bound infrastructure such as Neo4j connectivity, document storage root, multipart limits, actuator exposure, tracing exporter setup, Spring AI bootstrap mode, or Spring auto-configuration
- **THEN** the settings API reports it as not live-applied if it is exposed for visibility
- **AND** update and clear requests for that setting are rejected unless a dedicated safe runtime reconfiguration path exists
- **AND** updating live-only behavior does not attempt to rebuild unrelated infrastructure

#### Scenario: AI provider defaults are profile-managed
- **WHEN** a setting represents AI provider base URL, API key, chat model, embedding model, or embedding dimensions used to seed or configure provider defaults
- **THEN** the settings API reports the value as profile-managed or startup-bound according to its use
- **AND** clients use the AI profile management API to change active knowledge-base AI provider behavior

### Requirement: Runtime settings fall back to startup defaults
The system SHALL use configured startup properties as defaults when no persisted runtime override exists for a mutable setting.

#### Scenario: No override exists
- **WHEN** the application resolves an allowlisted mutable setting that has no persisted override
- **THEN** it uses the value bound from startup configuration

#### Scenario: Override is cleared
- **WHEN** a client clears a persisted override for an allowlisted mutable setting
- **THEN** the system falls back to the startup default for that setting

#### Scenario: Clear is requested for read-only setting
- **WHEN** a client clears a setting that is allowlisted only for read-only visibility
- **THEN** the system rejects the request
- **AND** no persisted runtime setting override is changed

### Requirement: Runtime settings catalog covers application property groups
The system SHALL expose a catalog entry for each relevant backend-owned application property group with explicit update semantics.

#### Scenario: Client lists expanded settings catalog
- **WHEN** a client requests the runtime settings list
- **THEN** the response includes catalog entries for application identity, logging, Spring AI bootstrap switches, AI provider startup defaults, Neo4j connection and database settings, document storage root, multipart upload limits, query settings, chunking settings, extraction settings, AI observability settings, actuator and tracing settings, OpenTelemetry exporter settings, and Spring auto-configuration controls
- **AND** each entry declares whether it is live-mutable, startup-bound, profile-managed, read-only, or sensitive read-only

#### Scenario: Active profiles override startup defaults
- **WHEN** a property value is supplied by an active Spring profile such as `openai`, `lm_studio`, or `langfuse`
- **THEN** the runtime settings list reports the profile-resolved startup value as the default for that setting

### Requirement: Sensitive runtime setting values are masked
The system SHALL prevent secret configuration values from being returned in runtime setting read responses.

#### Scenario: Client lists sensitive settings
- **WHEN** a client requests the runtime settings list
- **THEN** sensitive entries such as AI API keys, Neo4j passwords, and OTLP authorization headers are marked sensitive
- **AND** their current and default values do not contain the raw configured secret
- **AND** the response indicates whether the secret is configured when that can be reported without exposing the secret

#### Scenario: Client attempts to update sensitive startup-bound setting
- **WHEN** a client submits an update for a sensitive startup-bound setting through the runtime settings API
- **THEN** the system rejects the update
- **AND** no persisted runtime setting override is changed

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

