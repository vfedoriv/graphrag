## ADDED Requirements

### Requirement: AI profiles declare a non-secret structured-output mode
The system SHALL accept and persist an optional `structuredOutputMode` on AI profiles with supported values `PORTABLE` and `NATIVE_JSON_SCHEMA`. New profiles, seeded default profiles, and migrated existing profiles SHALL use `PORTABLE` when no mode is configured. Profile reads and knowledge-base profile configuration reads SHALL expose the effective non-secret mode without exposing API keys. Selecting native mode SHALL be an explicit operator choice and SHALL NOT trigger automatic provider probing during profile persistence.

#### Scenario: Existing client creates a profile without a mode
- **WHEN** a valid create request omits `structuredOutputMode` or supplies null
- **THEN** the saved profile uses `PORTABLE`
- **AND** subsequent profile reads expose `PORTABLE`

#### Scenario: Existing database is migrated
- **WHEN** the application migrates profile rows created before structured-output mode was introduced
- **THEN** those profiles use `PORTABLE`
- **AND** their secrets, identities, existing revisions, default selection, and knowledge-base assignments remain unchanged

#### Scenario: Startup seeds the default profile
- **WHEN** startup seeds a previously absent default AI profile
- **THEN** the seeded structured-output mode is `PORTABLE`
- **AND** startup does not overwrite the mode of an already persisted default profile

#### Scenario: Operator enables native output
- **WHEN** a valid profile create or update request explicitly selects `NATIVE_JSON_SCHEMA`
- **THEN** the mode is persisted and exposed through non-secret configuration reads
- **AND** the save operation does not call the provider to test support
- **AND** API keys remain write-only

#### Scenario: Unsupported mode is submitted
- **WHEN** a profile request contains an unsupported structured-output mode value
- **THEN** the request fails with the existing validation problem envelope
- **AND** no profile values, revision, default selection, or assignments change

### Requirement: Structured-output mode follows profile revision and compatibility semantics
The system SHALL include structured-output mode in profile revision and model-cache invalidation behavior. An update omitting the mode or supplying null SHALL retain the saved mode; an explicit `PORTABLE` update SHALL disable native output for subsequent resolutions. A mode-only change SHALL NOT change embedding identity or require corpus re-embedding. Existing optimistic concurrency, secret retention, default-profile, and embedding compatibility protections SHALL remain applicable.

#### Scenario: Older client updates a native-enabled profile
- **WHEN** a valid update request omits `structuredOutputMode` or supplies null on a native-enabled profile
- **THEN** the saved mode remains `NATIVE_JSON_SCHEMA`
- **AND** existing omitted-secret retention behavior is preserved

#### Scenario: Mode-only update with existing embeddings
- **WHEN** an operator changes only the output mode on a profile assigned to knowledge bases with existing embeddings
- **THEN** the valid update succeeds and advances the profile revision using existing mutation semantics
- **AND** subsequent model resolutions observe the new mode
- **AND** no embeddings or graph indexes are rebuilt

#### Scenario: Explicit portable rollback
- **WHEN** an operator saves `PORTABLE` on a native-enabled profile
- **THEN** subsequently resolved eligible calls use portable output
- **AND** already captured operations retain their captured model and mode

#### Scenario: Incompatible embedding change accompanies a mode edit
- **WHEN** a mode edit also attempts an embedding change incompatible with stored embeddings
- **THEN** the existing compatibility check rejects the entire profile update
- **AND** the prior mode, revision, and assignments remain unchanged

#### Scenario: Stale profile mutation
- **WHEN** a mode-bearing update conflicts with the existing optimistic concurrency rules
- **THEN** the update is rejected as a conflict
- **AND** the persisted profile configuration remains unchanged
