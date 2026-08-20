# Curated API map

All application routes use `/api/v1`. This page maps the workflow entry points; it does not duplicate every query parameter or DTO field. With the backend running, use [Swagger UI](http://localhost:8080/swagger-ui/index.html) for interactive requests and [`/v3/api-docs`](http://localhost:8080/v3/api-docs) for the exhaustive OpenAPI contract.

| Area | Core routes | Guide |
|---|---|---|
| Knowledge bases | `POST/GET /knowledge-bases`, `GET/PUT/DELETE /knowledge-bases/{id}` | [KBs and profiles](../workflows/knowledge-bases-profiles.md) |
| AI profiles | `POST/GET /ai-profiles`, `GET/PUT/DELETE /ai-profiles/{id}`, `GET/PUT /knowledge-bases/{id}/ai-profile` | [KBs and profiles](../workflows/knowledge-bases-profiles.md) |
| Schema registry | `POST/GET /schemas`, `GET/PUT/DELETE /schemas/{id}`, `/schemas/validate`, generation/example routes, KB list/attach/activate | [Schemas](../workflows/schemas.md) |
| Review-only discovery | `POST /knowledge-bases/{id}/schemas/discover`, `/discover/from-files` | [Schemas](../workflows/schemas.md) |
| Schema drafts | `/knowledge-bases/{id}/schema-drafts/**` for sources, analysis, candidates, decisions, conflicts, projection/diff, evaluation, readiness, publication | [Schema drafts](../workflows/schema-drafts.md) |
| Documents | KB-scoped upload/list/replace/delete; document process/options/chunk inspection | [Document processing](../workflows/document-processing.md) |
| Chunk migration | `GET /chunking-state`, `POST /knowledge-bases/{id}/chunk-migrations/preview` | [Chunking](../workflows/chunking-reprocessing.md) |
| Reprocessing | `POST/GET /knowledge-bases/{id}/reprocessing-plans`, detail and retry | [Chunking](../workflows/chunking-reprocessing.md) |
| Cypher | `POST /knowledge-bases/{id}/queries/generate|validate|execute|ask` | [Cypher](../workflows/cypher-queries.md) |
| Advanced search | readiness, submit/list/detail/result/cancel under `/knowledge-bases/{id}/queries/advanced-search-runs` | [Advanced search](../workflows/advanced-search.md) |
| Runtime settings | `GET/PUT /runtime-settings`, `PUT/DELETE /runtime-settings/{key}` | [Configuration](../operations/configuration.md) |

## Representative conventions

- Knowledge-base IDs are client-defined stable strings; most other resource IDs are server-generated opaque strings.
- JSON mutations use Bean Validation and return RFC 7807 on rejection.
- Upload/file discovery routes use multipart parts exactly as described by OpenAPI.
- Durable submissions return HTTP 202 plus status locations; poll rather than holding the submission connection.
- List/history/chunk APIs use bounded pages where the contract provides them. Compatibility complete-list routes are marked as such.
- Destructive and overwrite actions are explicit and ownership-scoped.

## OpenAPI handoff

The portal owns ordering, invariants, state, failure/recovery behavior, and representative payloads. OpenAPI owns exact media types, required/optional fields, validation bounds, enums, response schemas, and all controller annotations. When they differ, treat the running version's OpenAPI as the request-shape authority and file a documentation-alignment fix.
