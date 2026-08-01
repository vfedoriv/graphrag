## ADDED Requirements

### Requirement: Runtime settings remain chunking mutations rather than aggregate state
The runtime-settings API SHALL remain the mutation mechanism for allowlisted chunk settings, while the dedicated chunking-state resource SHALL be authoritative for their combined effective revision, component revisions, lifecycle, and alias precedence.

#### Scenario: Chunk settings are updated in bulk
- **WHEN** a valid atomic bulk update changes one or more chunking settings
- **THEN** subsequent chunking-state reads return the refreshed aggregate and no migration plan is automatically created

#### Scenario: Existing client reads setting metadata
- **WHEN** a client reads runtime-setting responses during the compatibility window
- **THEN** existing effective revision and migration lifecycle fields remain available even though new clients use the aggregate resource

#### Scenario: Settings mutation response remains compatible
- **WHEN** a chunk setting update succeeds
- **THEN** the existing response envelope remains valid and clients can deterministically refetch the documented chunking-state resource
