## Context

`SchemaController` currently exposes schema create, generate, validate, list, retrieve, and activate operations under `/api/v1`. `SchemaRegistryService` stores schema definitions in Neo4j as `SchemaDefinitionNode` records and enforces immutable `name + version` on creation. Knowledge bases point at active schemas through `KnowledgeBaseNode.activeSchemaId` and `(:KnowledgeBase)-[:USES_SCHEMA]->(:SchemaDefinition)` relationships.

The new API must allow ordinary clients to correct or remove schema records without weakening existing constraints: schema content remains JSON-only, parsed schema definitions remain valid, and active-schema invariants remain intact.

## Goals / Non-Goals

**Goals:**
- Add `PUT /api/v1/schemas/{schemaId}` for replacing persisted schema definition content and optional source type.
- Add `DELETE /api/v1/schemas/{schemaId}` for removing schema definitions that are safe to remove.
- Reuse existing schema parsing, validation, hashing, response, logging, and error conventions.
- Preserve `name + version` immutability by rejecting updates whose content changes either identity field.
- Reject update/delete when a schema is active for any knowledge base.
- Allow inactive associated schemas to be updated, and allow inactive associated schemas to be deleted by detaching their `USES_SCHEMA` relationships first.

**Non-Goals:**
- No partial patch endpoint for individual schema fields.
- No schema version renaming or version migration.
- No cascade deletion of extracted graph data, documents, knowledge bases, or query history.
- No changes to schema generation endpoints; generated schemas remain review-only until saved.

## Decisions

1. Use full replacement update semantics with `PUT /schemas/{schemaId}`.
   - Rationale: schema definitions are stored as a JSON document and validated as a unit. Full replacement avoids ambiguous partial edits inside schema JSON.
   - Alternative considered: `PATCH` for individual metadata/content fields. Rejected because JSON schema internals need whole-document validation anyway.

2. Keep `name + version` immutable on update.
   - Rationale: the project explicitly treats schema versions as immutable identities. Allowing content correction is useful, but changing identity would make existing references ambiguous.
   - Alternative considered: create a new version automatically when identity changes. Rejected because version creation is already explicit through `POST /schemas`.

3. Return `SchemaDetailsResponse` from update and `204 No Content` from delete.
   - Rationale: update callers need the persisted content hash and content confirmation; delete callers only need success/failure.
   - Alternative considered: return `SchemaResponse` from update. Rejected because `GET /schemas/{schemaId}` already has the content-bearing response shape.

4. Prevent update/delete of active schemas, but allow mutation of inactive associated schemas.
   - Rationale: active schemas are part of current extraction/query behavior. Inactive schema associations are discoverability links and should not make ordinary cleanup impossible.
   - Alternative considered: reject all associated schemas. Rejected because schemas are commonly associated with knowledge bases, which would make update/delete unusable in normal workflows.

5. Detach inactive `USES_SCHEMA` relationships before deleting a schema.
   - Rationale: deleting an inactive schema should not leave stale knowledge-base relationships, and clearing those relationships does not alter `activeSchemaId`.
   - Alternative considered: leave relationships for Neo4j delete behavior to handle. Rejected because explicit cleanup is clearer and easier to test.

## Risks / Trade-offs

- Active status is currently stored on the schema node while activation is scoped by knowledge base. A schema associated to multiple knowledge bases could have a coarse status value. -> Use `KnowledgeBase.activeSchemaId` as the authoritative active guard instead of relying only on node status.
- Full replacement update can alter labels and relationships in an inactive schema that may still have historical association value. -> Allow it because inactive schemas are not used for current extraction/query behavior; add extraction-run schema provenance later if audit-grade history becomes required.
- Existing clients may expect schema definitions to be immutable beyond identity. -> The API keeps identity immutable and requires explicit `PUT`, making the mutability boundary narrow and auditable.
- Deleting inactive associated schemas removes them from knowledge-base schema lists. -> This matches cleanup intent; active schemas remain protected from accidental deletion.
