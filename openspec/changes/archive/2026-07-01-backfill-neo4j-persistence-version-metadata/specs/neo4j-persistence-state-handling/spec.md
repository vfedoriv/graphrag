## MODIFIED Requirements

### Requirement: Assigned-id Neo4j entities use stable state detection
The system SHALL map backend-owned Neo4j application entities that use assigned business identifiers with Spring Data Neo4j-supported persistence state metadata.

#### Scenario: Assigned-id entity is saved
- **WHEN** the system saves a Neo4j application entity whose identifier is assigned by application code
- **THEN** the entity mapping includes persistence state metadata supported by Spring Data Neo4j
- **AND** normal save operations do not emit assigned-id new-entity warnings

#### Scenario: Entity has a business version field
- **WHEN** a Neo4j application entity already has a business field named `version`
- **THEN** persistence state metadata uses a distinct field name
- **AND** the business version meaning remains unchanged

#### Scenario: Existing entity lacks persistence version metadata
- **WHEN** the application starts with persisted Neo4j application entities created before persistence version metadata existed
- **THEN** the system backfills missing persistence version metadata before normal save/update operations depend on it
- **AND** existing entity values and business identifiers remain unchanged
