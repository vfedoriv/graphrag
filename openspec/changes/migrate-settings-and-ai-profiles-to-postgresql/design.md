## Context

Runtime setting overrides and AI profiles are currently Neo4j operational nodes. They have limited graph behavior but carry important invariants: typed setting allowlists, lifecycle state, live logging changes, one default profile, write-only API keys, and startup seeding. They are the first domain migration after the relational foundation.

## Goals / Non-Goals

**Goals:**

- Make PostgreSQL canonical for setting overrides and AI profiles.
- Preserve current API responses, secret handling, validation, and live behavior.
- Exercise assigned IDs, optimistic locking, enum checks, and partial uniqueness.

**Non-Goals:**

- Moving knowledge bases or their profile assignments.
- Making datasource or other bootstrap-critical settings mutable.
- Reading legacy Neo4j data or supporting dual writes.

## Decisions

1. **Create two focused tables.** `runtime_setting_override` stores the allowlisted key, desired value, lifecycle metadata needed for reconciliation, timestamps, and an optimistic version. `ai_profile` stores assigned identity, provider/model configuration, encrypted or existing protected API-key representation, default status, timestamps, and an optimistic version.

2. **Enforce one default profile in PostgreSQL.** A partial unique index on the default predicate prevents concurrent creation of multiple defaults. Service transactions translate constraint and optimistic-lock failures into existing conflict responses.

3. **Keep secrets write-only.** JPA mappings and DTO mappers never expose stored API keys. Normal application logs contain no credentials or provider payloads.

4. **Preserve catalog authority in code.** The runtime-setting catalog remains the source of truth for editability, typing, update mode, sensitivity, and lifecycle derivation. PostgreSQL stores only accepted overrides. Datasource URL, username, pool settings, and schema are deployment-managed entries; passwords are sensitive and masked.

5. **Seed transactionally and idempotently.** Startup creates a default profile from `app.model.*` only when no default exists. Concurrent startup is resolved by the database constraint and a read-after-conflict path.

## Risks / Trade-offs

- [Concurrent default changes violate uniqueness] → Perform demotion and promotion in one relational transaction backed by the partial index.
- [A secret leaks through entity serialization or logging] → Keep entities outside controller contracts, map explicitly, and add masking/logging tests.
- [Persisted setting lifecycle fields drift from the catalog] → Continue deriving or normalizing lifecycle state from the current catalog and active value.
- [Intermediate code still expects Neo4j profile nodes] → Keep KB assignment out of this change and expose profile lookup through stable service/adaptor interfaces.

## Migration Plan

1. Add Flyway tables and constraints.
2. Add relational entities, repositories, and adapters.
3. Switch services and startup seeding to relational transactions.
4. Add optimistic-lock, default uniqueness, masking, catalog, and API regression tests.
5. Remove settings/profile Neo4j callers; final label and initializer cleanup remains in the cutover change.

Rollback is application-level because the target GraphRAG databases are disposable before final cutover; no legacy-data importer or dual-write rollback is provided.

## Open Questions

- None.
