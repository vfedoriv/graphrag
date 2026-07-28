## ADDED Requirements

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
