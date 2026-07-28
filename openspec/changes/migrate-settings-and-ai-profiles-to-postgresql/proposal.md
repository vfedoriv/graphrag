## Why

Runtime settings and AI profiles are low-coupling operational aggregates and provide the safest first production use of the relational foundation. Moving them first validates relational persistence, optimistic updates, secret handling, and default-profile seeding before more connected domains move.

## What Changes

- Require `establish-relational-persistence-foundation` to be implemented first.
- Add Flyway-managed `runtime_setting_override` and `ai_profile` tables with assigned IDs, timestamps, enum checks, optimistic versions, and a partial unique constraint for the default profile.
- Replace Neo4j operational nodes and repositories for settings and profiles with JPA entities and relational persistence adapters.
- Preserve allowlisted typed settings, lifecycle metadata, live application behavior, default-profile seeding, write-only API keys, validation, and API response contracts.
- Expose PostgreSQL datasource properties as deployment-managed catalog entries while masking the password.
- Keep knowledge-base profile assignment outside this change.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `runtime-application-settings`: Store overrides relationally and report the PostgreSQL datasource through the deployment-managed settings catalog.
- `ai-profile-management`: Make PostgreSQL authoritative for AI profile CRUD, default selection, optimistic conflicts, and write-only credentials.

## Impact

- Affects runtime settings and AI profile domain models, repositories, services, bootstrap behavior, configuration metadata, migrations, controllers' persistence mappings, and focused integration tests.
- Removes these aggregates' runtime dependency on Neo4j while leaving current API contracts unchanged.
