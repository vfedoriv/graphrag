## Why

Local startup currently allows Spring Boot Docker Compose service-connection discovery to override GraphRAG's explicit datasource and route Flyway/JPA into `langfuse.app`. The project has not entered a data-preserving deployment phase, so the safest correction is to fix routing and perform a one-time destructive reset of GraphRAG-owned state.

## What Changes

- Prevent the shared `langfuse-postgres` Compose service from supplying an automatic application datasource while retaining Compose lifecycle support.
- Keep local GraphRAG startup explicitly routed through `GRAPHRAG_POSTGRES_*` to the dedicated `graphrag / graphrag / app` identity.
- Delete the accidentally created GraphRAG state and reset GraphRAG's dedicated PostgreSQL and Neo4j stores without preserving old GraphRAG data.
- Re-provision empty stores, start the application, and confirm the fresh topology works.
- Add a focused configuration regression test for the Compose exclusion and aligned datasource/provisioning defaults.
- Keep startup, reset, and contributor guidance synchronized without introducing migration, backup, quarantine, or recurring cutover-verifier procedures.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `shared-postgresql-isolation`: Require Compose discovery to leave GraphRAG's explicit dedicated datasource untouched during local startup.
- `polyglot-persistence-cutover`: Define the current correction as a reset-only fresh start with no GraphRAG data preservation.
- `test-coverage-governance`: Add focused configuration coverage for datasource precedence and default alignment.
- `documentation-alignment`: Keep the simple fresh-start reset and startup commands synchronized across operator and contributor documentation.

## Impact

- Affected configuration: `compose.yaml` and existing GraphRAG datasource/provisioning defaults.
- Affected verification: a lightweight configuration regression test plus the existing deterministic test suite.
- Affected operations: one-time deletion and recreation of GraphRAG-owned PostgreSQL and Neo4j state.
- No public REST API, JPA data model, recurring startup validation, or data migration capability is introduced.
