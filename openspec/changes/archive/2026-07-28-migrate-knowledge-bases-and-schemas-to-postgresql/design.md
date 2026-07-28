## Context

Knowledge bases, schema definitions, associations, activation state, and profile assignment currently use Neo4j nodes and relationships. These aggregates are authoritative metadata rather than graph facts. Documents and schema-draft workflows need stable relational foreign keys to them.

## Goals / Non-Goals

**Goals:**

- Make PostgreSQL authoritative for KB and schema identity, association, activation, and profile assignment.
- Preserve immutable schema identity and all existing controller behavior.
- Enforce profile compatibility and activation invariants atomically.

**Non-Goals:**

- Moving documents, chunks, extracted facts, or draft workflows in this change.
- Changing schema JSON format or API representations.
- Migrating existing Neo4j metadata.

## Decisions

1. **Model core ownership relationally.** `knowledge_base` references the assigned AI profile. `schema_definition` owns immutable `(name, version)` identity and stored schema text. `knowledge_base_schema` records KB association and activation state with constraints that support one active schema per KB.

2. **Use assigned string IDs and optimistic versions.** IDs remain stable across PostgreSQL and Neo4j references. Mutable aggregate roots use JPA `@Version`; immutable identity changes remain service-level validation errors.

3. **Preserve schema JSON as text.** Stored content remains byte-for-byte suitable for current parsing, hashing, fingerprinting, and API mapping. JSONB is deferred.

4. **Make activation one relational transaction.** Lock or conditionally update the KB association set so deactivation and activation cannot leave multiple active schemas. Active schemas remain immutable and undeletable.

5. **Validate embedding compatibility before assignment.** A KB profile change reads authoritative chunk-existence/embedding-space information through a graph port, then performs a conditional relational update. If compatibility fails or the relational version changes, the previous assignment remains intact.

6. **Keep domain services persistence-neutral where practical.** Controllers retain DTOs. Registry, bootstrap, and resolver services depend on relational adapters rather than JPA entities, limiting persistence leakage.

## Risks / Trade-offs

- [Intermediate Neo4j queries expect KB/schema nodes] → Adapt callers in dependency order and use stable IDs at store boundaries until graph purification.
- [Concurrent activation produces two active associations] → Combine database constraints with a locking or conditional-update transaction.
- [Cross-store compatibility check races with chunk creation] → Reject known incompatibility before assignment and retain database-side optimistic conflict protection; document processing rechecks its active profile.
- [Text schema payloads are not queryable relational JSON] → Accept the limitation to preserve fingerprints and defer JSONB conversion.

## Migration Plan

1. Add the three tables, indexes, checks, and foreign keys.
2. Add entities/adapters and migrate schema registry, bootstrap, activation, retrieval, listing, and KB lifecycle.
3. Move KB profile assignment and compatibility handling.
4. Replace tests that inspect operational Neo4j nodes with API and PostgreSQL assertions while retaining graph behavior tests.
5. Leave legacy operational graph cleanup for final cutover.

Rollback assumes empty target stores and reverts application code; no online backward data conversion is supported.

## Open Questions

- The implementation may choose a partial unique index or a KB-owned active-schema foreign key, provided activation remains atomic and existing API semantics are preserved.
