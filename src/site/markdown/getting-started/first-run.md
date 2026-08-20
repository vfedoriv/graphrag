# First end-to-end run

This walkthrough creates a knowledge base, activates a schema, uploads and processes a document, and asks a schema-aware question. Start the persistence services and an AI-enabled backend as described in [local setup](local-setup.md).

Use one stable client-defined knowledge-base ID throughout:

```bash
export GRAPHRAG_API=http://localhost:8080/api/v1
export KB_ID=kb-demo
```

## 1. Inspect schemas

Bootstrap schemas are loaded idempotently from `src/main/resources/schemas/*.json`:

```bash
curl "$GRAPHRAG_API/schemas"
```

Copy a compatible schema's `id` from the response.

## 2. Create the knowledge base

```bash
curl -X POST "$GRAPHRAG_API/knowledge-bases" \
  -H "Content-Type: application/json" \
  -d '{"id":"kb-demo","name":"Demo knowledge base"}'
```

New knowledge bases inherit the current default AI profile. Confirm or change the assignment through `GET` or `PUT /knowledge-bases/{knowledgeBaseId}/ai-profile` before chunks exist.

## 3. Activate the schema

```bash
curl -X POST \
  "$GRAPHRAG_API/knowledge-bases/$KB_ID/schemas/<schemaId>/activate"
```

Activation makes the schema the extraction and query-safety boundary. It can lazily create a missing knowledge base for backward compatibility, but explicit creation makes the profile and ownership steps clearer.

## 4. Upload and process a document

```bash
curl -X POST "$GRAPHRAG_API/knowledge-bases/$KB_ID/documents" \
  -F "file=@/absolute/path/to/document.pdf"

curl -X POST "$GRAPHRAG_API/documents/<documentId>/process"
```

Processing is synchronous: parse TXT/PDF/DOCX, create hierarchical or flat chunks from the snapshotted settings, embed retrieval chunks, persist them in Neo4j, extract schema-constrained facts, validate them, and write evidence/provenance. A repeated completed run needs `?allowOverwrite=true`.

## 5. Ask a question

```bash
curl -X POST "$GRAPHRAG_API/knowledge-bases/$KB_ID/queries/ask" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"What obligations does the supplier have?"}'
```

`/ask` generates Cypher from the active schema, validates it as read-only, runs Neo4j `EXPLAIN`, enforces the configured row limit, and only then executes it.

## 6. Try durable advanced search

```bash
curl "$GRAPHRAG_API/knowledge-bases/$KB_ID/queries/advanced-search-runs/readiness"

curl -X POST \
  "$GRAPHRAG_API/knowledge-bases/$KB_ID/queries/advanced-search-runs" \
  -H "Content-Type: application/json" \
  -d '{"query":"When does the agreement renew?","maximumEvidence":10,"includeEvidenceText":true}'
```

The submission returns HTTP 202 and durable links. Poll the run link until a terminal status, then fetch its result link. A `PARTIAL` result can still contain useful cited evidence; inspect diagnostics and limitations.

## Failure behavior

- HTTP 400: malformed input, invalid schema, or unsafe query.
- HTTP 404: referenced knowledge base, schema, or document does not exist.
- HTTP 409: incompatible profile assignment, active-schema mutation, processing overwrite guard, or readiness blocker.
- HTTP 5xx: persistence/provider failure or unexpected processing error; document/run state records the failure where the workflow is durable.

All errors use RFC 7807 `application/problem+json`. See the [error guide](../reference/errors.md) and runtime OpenAPI for exact fields.
