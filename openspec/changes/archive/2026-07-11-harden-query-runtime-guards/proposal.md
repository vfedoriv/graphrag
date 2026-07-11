## Why

The query API reports a maximum row count and timeout but currently only adds a limit when none is present and does not apply a driver-level execution deadline. Validation output can also report startup values after live runtime overrides, leaving clients with an inaccurate safety contract.

## What Changes

- Enforce configured maximum rows for every executable query, including queries that contain an explicit limit.
- Enforce configured query timeouts at the Neo4j execution layer and expose the effective runtime values consistently.
- Execute planner validation and user-supplied Cypher through the application's existing Neo4j driver with a per-request transaction timeout.
- **BREAKING** Reject queries whose explicit limit exceeds the configured maximum.

## Capabilities

### New Capabilities
- `query-execution-guardrails`: provides enforced execution limits and deadlines for user queries.

### Modified Capabilities
- `cypher-validation`: validate explicit limits against runtime policy and return the effective executable query contract.
- `runtime-application-settings`: ensure live query safety settings are applied and represented consistently by every query API path.

## Impact

Affected Cypher validation and execution services, query controller responses, Neo4j driver transaction handling, error handling, runtime settings, and integration tests.
