# query-execution-guardrails Specification

## Purpose
TBD - created by archiving change harden-query-runtime-guards. Update Purpose after archive.
## Requirements
### Requirement: Every executable query has an enforced row bound
The system SHALL execute user-supplied Cypher only with a row limit that is less than or equal to the effective runtime maximum.

#### Scenario: Query omits limit
- **WHEN** a valid query omits a top-level limit and the runtime policy requires one
- **THEN** the system injects the effective maximum row limit before validation and execution

#### Scenario: Query has oversized literal limit
- **WHEN** a query specifies a literal top-level limit greater than the effective runtime maximum
- **THEN** the system rejects the query before `EXPLAIN` or execution
- **AND** the response identifies the configured maximum

#### Scenario: Query has oversized bound limit
- **WHEN** a query specifies a bound top-level limit parameter whose supplied value exceeds the effective runtime maximum
- **THEN** the system rejects the query before execution

### Requirement: Query execution has an enforced deadline
The system SHALL apply the effective runtime query timeout to planner validation and execution at the Neo4j driver or transaction boundary.

#### Scenario: Query exceeds deadline
- **WHEN** planner validation or execution exceeds the effective timeout
- **THEN** the system terminates the database operation
- **AND** returns a stable timeout problem response without raw query content

### Requirement: Query guardrails use the configured application database connection
The system SHALL execute planner validation and validated user Cypher through the application's configured Neo4j driver and database selection.

#### Scenario: Query execution starts
- **WHEN** a user query passes textual and schema validation
- **THEN** planner validation and execution use the existing application Neo4j connection
- **AND** no additional Neo4j instance, driver credentials, or dedicated query principal is required

