## Context

The backend currently supports global schema creation through `POST /api/v1/schemas` and knowledge-base-scoped listing through `GET /api/v1/knowledge-bases/{knowledgeBaseId}/schemas`. The scoped list only returns schemas connected to the knowledge base by `USES_SCHEMA`; that relationship is currently created as a side effect of activation.

The frontend creates a schema while the user is in a selected workspace, then refreshes the workspace schema list. The created schema is persisted globally but does not appear in the workspace list because no association exists.

## Goals / Non-Goals

**Goals:**

- Let clients associate a newly created schema with a knowledge base during schema creation.
- Let clients attach an existing schema to a knowledge base without activating it.
- Preserve the existing single-active-schema behavior: association is not activation.
- Keep global schema creation available for clients that do not pass a knowledge base identifier.
- Return established RFC 7807 `ProblemDetail` errors for missing knowledge bases, missing schemas, invalid payloads, and conflicts.

**Non-Goals:**

- Changing the active-schema selection workflow.
- Introducing multiple active schemas per knowledge base.
- Migrating existing unattached schemas automatically.
- Changing the global schema list semantics.

## Decisions

1. Extend schema creation with optional `knowledgeBaseId`.

   `POST /api/v1/schemas` will accept the existing schema content plus an optional knowledge base identifier. When present, the service will validate the knowledge base exists, create the schema, and `MERGE` the `USES_SCHEMA` relationship in the same transactional operation. The created schema remains `INACTIVE` unless separately activated.

   Alternative considered: require the frontend to call activation after creation. That would make the schema visible, but it would also switch the active schema, which is not always the intended user action.

2. Add a KB-scoped attach endpoint for existing schemas.

   Add `POST /api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach` to create the `USES_SCHEMA` relationship without changing `activeSchemaId` or schema status. The operation should be idempotent through `MERGE`.

   Alternative considered: reuse the activation endpoint. Activation has stronger semantics and intentionally deactivates siblings, so overloading it would keep the current UI bug coupled to a state change.

3. Keep `USES_SCHEMA` as the association source of truth.

   The existing KB list query already models workspace membership as a graph relationship. The fix should create that relationship at the correct time rather than broadening the list endpoint to include global schemas.

   Alternative considered: have the frontend display global schemas alongside KB schemas. That would blur workspace membership and would not solve backend consistency for other clients.

## Risks / Trade-offs

- Duplicate association requests -> Use Cypher `MERGE` so repeated create/attach flows do not create duplicate relationships.
- Partial writes during create-with-association -> Keep schema persistence and relationship creation inside one transactional service method.
- Existing clients may not send `knowledgeBaseId` -> Treat the new field as optional and preserve global create behavior.
- Ambiguous frontend expectation around activation -> Document and test that association makes the schema listable but does not make it active.
