# runtime-application-settings Specification

## Purpose
TBD - created by archiving change add-runtime-settings-ai-profiles. Update Purpose after archive.
## Requirements
### Requirement: Runtime settings are exposed through an allowlisted API
The system SHALL expose application settings only through an explicit allowlist of backend-owned settings, including mutable live settings, mutable restart-required settings, and non-mutable configuration inventory entries.

#### Scenario: Client lists runtime settings
- **WHEN** a client requests the runtime settings list
- **THEN** the response includes each allowlisted setting with key, category, value type, current value, default value, source, mutability, live-apply status, sensitivity, validation constraints, update mode, and read-only or restart reason when applicable
- **AND** the response includes an explicit lifecycle state for settings whose saved value is pending restart or active
- **AND** settings that are editable through the settings API report `mutable=true` whether they apply live or after restart
- **AND** settings that are not editable through the settings API report `mutable=false`
- **AND** non-allowlisted Spring, system, management, Neo4j, storage, multipart, and application properties are not exposed as mutable settings

#### Scenario: Client requests a non-allowlisted setting update
- **WHEN** a client submits an update for a setting key that is not in the allowlist
- **THEN** the system rejects the update
- **AND** no persisted runtime setting is changed

### Requirement: Runtime setting updates are validated and persisted
The system SHALL validate submitted runtime setting values before persisting mutable live overrides or mutable restart-required overrides in PostgreSQL, and SHALL persist runtime setting override records using stable entity state handling for assigned setting-key identifiers.

#### Scenario: Valid live setting update is submitted
- **WHEN** a client updates an allowlisted mutable live setting with a valid value
- **THEN** the system persists the override
- **AND** subsequent setting reads return the persisted value as the current value
- **AND** subsequent workflow executions use the updated value without requiring application restart

#### Scenario: Valid restart-required setting update is submitted
- **WHEN** a client updates an allowlisted mutable restart-required setting with a valid value
- **THEN** the system persists the override
- **AND** subsequent setting reads return the persisted value as the desired current value
- **AND** the response reports the saved value as pending
- **AND** the response indicates that the updated value is not live-applied and requires application restart before it affects startup-bound behavior

#### Scenario: Existing runtime setting override is updated
- **WHEN** a client updates an allowlisted mutable setting that already has a persisted override record
- **THEN** the system updates the existing override record for that setting key
- **AND** the save path preserves persistence state and optimistic versioning for assigned identifiers
- **AND** normal repeated updates do not emit assigned-id new-entity warnings
- **AND** subsequent setting reads return the latest persisted value and lifecycle metadata

#### Scenario: Existing runtime setting override lacks version metadata
- **WHEN** the application loads persisted runtime setting overrides created before override records included version metadata
- **THEN** the system normalizes missing version metadata before versioned persistence operations run
- **AND** runtime settings list, update, clear, and lifecycle reconciliation operations do not fail because an existing override record lacks version metadata
- **AND** existing override values and lifecycle metadata remain available after normalization

#### Scenario: Restart-required setting is active after restart
- **WHEN** the application starts and loads a persisted restart-required override for a supported mutable setting
- **THEN** the affected setting response no longer reports the override as pending
- **AND** the response indicates that the saved value is the active value for the running application

#### Scenario: Existing override has stale lifecycle state
- **WHEN** an existing persisted runtime setting override has missing, stale, or contradictory lifecycle state
- **THEN** the system derives the effective lifecycle state from the current setting definition, persisted value, active runtime value, and update mode
- **AND** the setting response does not expose lifecycle state that contradicts the current catalog behavior
- **AND** if lifecycle state is stored persistently, the system updates it to the derived value during reconciliation

#### Scenario: Invalid setting update is submitted
- **WHEN** a client updates an allowlisted mutable setting with a value that violates type, range, or collection constraints
- **THEN** the system rejects the update with a validation error
- **AND** the previous setting value remains active
- **AND** no pending restart override is changed

#### Scenario: Non-mutable setting update is submitted
- **WHEN** a client updates an allowlisted setting whose update mode is read-only, profile-managed, sensitive read-only, or restart-required but not marked mutable
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

#### Scenario: Mutable startup-bound setting is changed
- **WHEN** a client changes a mutable restart-required setting that controls startup-bound behavior
- **THEN** the settings API persists and reports the desired value
- **AND** the running application does not attempt to rebuild startup-bound infrastructure for that value
- **AND** the settings API reports that application restart is required before the value is active
- **AND** updating live-only behavior does not attempt to rebuild unrelated infrastructure

#### Scenario: Non-mutable startup-bound setting is represented
- **WHEN** a setting controls startup-bound behavior that is not safe or supported for persisted restart-required edits
- **THEN** the settings API reports it as not live-applied if it is exposed for visibility
- **AND** update and clear requests for that setting are rejected
- **AND** updating live-only behavior does not attempt to rebuild unrelated infrastructure

#### Scenario: Pre-relational-applied setting has no reassignment path
- **WHEN** a setting value is consumed before PostgreSQL-backed runtime overrides can be loaded
- **AND** the running application has no safe path to reassign that value after startup
- **THEN** the settings API exposes it only as read-only or sensitive read-only inventory
- **AND** update and clear requests for that setting are rejected

#### Scenario: Neo4j connectivity remains deployment-managed
- **WHEN** a setting controls Neo4j URI, authentication, credentials, or database selection
- **THEN** the settings API exposes it only as read-only or sensitive read-only inventory
- **AND** clients must change the value through deployment configuration such as environment variables or Docker Compose
- **AND** update and clear requests for that setting are rejected

#### Scenario: Logging level is editable
- **WHEN** a client changes the allowlisted root logging level setting with a valid value
- **THEN** the system persists the override
- **AND** the setting response reports whether the new logging level is live-applied or pending according to the implemented logging update mode

#### Scenario: AI provider defaults are profile-managed
- **WHEN** a setting represents AI provider base URL, API key, chat model, embedding model, or embedding dimensions used to seed or configure provider defaults
- **THEN** the settings API reports the value as profile-managed or startup-bound according to its use
- **AND** clients use the AI profile management API to change active knowledge-base AI provider behavior

### Requirement: Runtime settings fall back to startup defaults
The system SHALL use configured startup properties as defaults when no persisted override exists for a mutable setting.

#### Scenario: No override exists
- **WHEN** the application resolves an allowlisted mutable setting that has no persisted override
- **THEN** it uses the value bound from startup configuration

#### Scenario: Live override is cleared
- **WHEN** a client clears a persisted override for an allowlisted mutable live setting
- **THEN** the system falls back to the startup default for that setting
- **AND** subsequent workflow executions use the startup default without requiring application restart

#### Scenario: Restart-required override is cleared
- **WHEN** a client clears a persisted override for an allowlisted mutable restart-required setting
- **THEN** subsequent setting reads fall back to the startup default for that setting
- **AND** the response indicates restart behavior consistently with whether the running application was already using the cleared value

#### Scenario: Clear is requested for non-mutable setting
- **WHEN** a client clears a setting that is allowlisted only for non-mutable visibility
- **THEN** the system rejects the request
- **AND** no persisted runtime setting override is changed

### Requirement: Runtime settings catalog covers application property groups
The system SHALL expose a catalog entry for each relevant backend-owned application property group with explicit update semantics.

#### Scenario: Client lists expanded settings catalog
- **WHEN** a client requests the runtime settings list
- **THEN** the response includes catalog entries for application identity, logging, Spring AI bootstrap switches, AI provider startup defaults, Neo4j connection and database settings, document storage root, multipart upload limits, query settings, chunking settings, extraction settings, AI observability settings, actuator and tracing settings, OpenTelemetry exporter settings, and Spring auto-configuration controls
- **AND** each entry declares whether it is live-mutable, restart-required mutable, profile-managed, read-only, or sensitive read-only
- **AND** Neo4j connectivity entries are not restart-required mutable
- **AND** pre-Neo4j-applied entries without safe runtime reassignment are not restart-required mutable
- **AND** root logging level is mutable

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
- **THEN** the system persists each submitted override
- **AND** the response contains the updated runtime setting representations for the submitted keys in request order
- **AND** subsequent setting reads return each persisted value with active or pending lifecycle state according to each setting's update mode

#### Scenario: Bulk update contains mixed live and restart-required settings
- **WHEN** a client submits a valid bulk update request containing both live and restart-required mutable setting keys
- **THEN** the system persists all submitted overrides atomically
- **AND** live settings are applied to subsequent workflow executions without restart
- **AND** restart-required settings are reported as requiring application restart before they affect startup-bound behavior

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

### Requirement: Query safety settings describe effective execution behavior
The system SHALL apply and report live query maximum-row and timeout settings consistently across query validation, generation, ask, and execution.

#### Scenario: Live query timeout changes
- **WHEN** a client changes the live query timeout setting
- **THEN** subsequent planner validation and query execution use the updated timeout
- **AND** all corresponding query responses report the updated effective timeout

#### Scenario: Query settings response is generated
- **WHEN** a query API response includes maximum rows or timeout metadata
- **THEN** the values come from the same runtime policy snapshot used to process that request

### Requirement: Live discovery budgets govern subsequent draft runs
The system SHALL apply live schema-discovery source-concurrency, source-timeout, and request-timeout settings to subsequently created durable schema-draft analysis runs through an immutable typed execution-policy snapshot.

#### Scenario: Source concurrency changes
- **WHEN** a client updates `app.schema-discovery.max-concurrency` with a valid live value
- **THEN** a subsequently created draft run captures and enforces the updated per-run source-concurrency limit
- **AND** already created runs retain their prior captured limit

#### Scenario: Discovery timeouts change
- **WHEN** a client updates the live schema-discovery source or request timeout with a valid value
- **THEN** a subsequently created draft run captures and enforces the updated timeout
- **AND** its reuse settings fingerprint and effective budget metadata describe the same captured policy

#### Scenario: Live override is cleared
- **WHEN** a client clears a persisted discovery execution-budget override
- **THEN** subsequently created draft runs capture the startup default
- **AND** existing runs retain the policy captured when they were created

### Requirement: Discovery budgets remain distinct from AI profile transport settings
The system SHALL represent discovery source/request deadlines separately from AI-profile HTTP timeout and SDK retry settings and SHALL NOT silently rewrite either configuration to match the other.

#### Scenario: Provider retry envelope exceeds workflow deadline
- **WHEN** an AI profile's configured timeout and retry envelope can outlast a captured source or request deadline
- **THEN** the draft run still enforces its captured workflow deadline as a result-acceptance boundary
- **AND** the backend emits privacy-safe configuration metadata sufficient to diagnose the mismatch
- **AND** the stored AI profile remains unchanged

### Requirement: Runtime setting overrides are relational operational state
The system SHALL persist accepted runtime setting overrides in PostgreSQL using typed, allowlisted, optimistic updates while retaining the catalog as the authority for editability, sensitivity, update mode, and lifecycle behavior.

#### Scenario: A live setting is updated
- **WHEN** a valid mutable live setting is saved
- **THEN** the relational override commits atomically
- **AND** the active runtime behavior and reported lifecycle state remain consistent with the catalog

#### Scenario: Concurrent updates conflict
- **WHEN** two callers update the same override from the same prior version
- **THEN** one update succeeds
- **AND** the stale update receives the existing conflict response

### Requirement: PostgreSQL datasource settings are deployment-managed
The settings catalog SHALL report supported GraphRAG datasource and pool properties as deployment-managed and SHALL keep datasource credentials non-mutable and masked.

#### Scenario: Settings are listed
- **WHEN** a caller lists runtime settings
- **THEN** supported PostgreSQL URL, username, database/schema, and pool metadata are identified as deployment-managed
- **AND** the datasource password value is not returned
