## MODIFIED Requirements

### Requirement: Runtime setting updates are validated and persisted
The system SHALL validate submitted runtime setting values before persisting mutable live overrides or mutable restart-required overrides in Neo4j or the configured runtime settings store, and SHALL persist runtime setting override records using stable entity state handling for assigned setting-key identifiers.

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
- **AND** the save path preserves persistence state needed by Spring Data Neo4j for assigned identifiers
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
