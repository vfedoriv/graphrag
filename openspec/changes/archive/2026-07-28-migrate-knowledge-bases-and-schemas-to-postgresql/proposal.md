## Why

Knowledge bases and schemas define ownership, validation, activation, and AI-profile compatibility for nearly every workflow. They must become relational before document and draft workflows can use PostgreSQL foreign keys and deterministic ownership constraints.

## What Changes

- Require the relational foundation and PostgreSQL-backed AI profiles.
- Add Flyway-managed `knowledge_base`, `schema_definition`, and `knowledge_base_schema` tables with immutable schema identity, ownership constraints, optimistic versions, and relational profile references.
- Replace operational Neo4j persistence for knowledge bases and schemas with JPA entities and adapters.
- Preserve schema parsing, validation, inactive-schema mutation guards, activation, bootstrap behavior, KB association, pagination, sorting, and all `/api/v1` representations.
- Enforce AI profile embedding compatibility transactionally and leave the previous assignment unchanged on failure.
- Retain extracted graph labels, relationship types, and dynamic Cypher behavior in Neo4j.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `knowledge-base-lifecycle`: Make relational records and foreign keys authoritative for KB identity, lifecycle, and profile assignment.
- `schema-mutation`: Persist schema definitions relationally while preserving immutable identity and inactive-only mutation behavior.
- `single-active-schema-per-knowledge-base`: Enforce activation and association invariants through relational transactions and constraints.
- `active-schema-resolution`: Resolve active schema content from PostgreSQL without changing caller-visible behavior.
- `schema-list-by-knowledge-base`: Serve ownership-safe schema listings through relational associations.
- `ai-profile-management`: Enforce relational KB profile assignment and embedding compatibility atomically.

## Impact

- Affects KB and schema entities, repositories, registry/bootstrap/activation services, profile assignment, controllers' persistence mappings, Flyway migrations, and integration tests.
- Establishes prerequisites for document and schema-draft foreign keys while leaving graph extraction and query execution in Neo4j.
