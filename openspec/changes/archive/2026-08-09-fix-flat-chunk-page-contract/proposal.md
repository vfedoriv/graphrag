## Why

The Chunk Explorer already requests `kind=FLAT` when a document contains unparented chunks, but the backend currently rejects that selector because it only accepts persisted `PARENT` and `CHILD` kinds. Substituting `CHILD` would mix parented and unparented children in mixed documents, so the bounded page contract needs an explicit virtual read filter.

## What Changes

- Accept `kind=FLAT` on the bounded document chunk page endpoint, case-insensitively like the existing kind filters.
- Define `FLAT` as the read predicate `chunk.kind = CHILD AND chunk.parentChunkId IS NULL`; return matching records with their persisted `kind=CHILD`.
- Apply the same flat predicate, section filter, and deterministic `chunkIndex ASC, id ASC` ordering to page content and total counts.
- Reject `kind=FLAT` combined with a concrete `parentChunkId` as an RFC 7807 `400 Bad Request` with a stable explanation.
- Update the controller/OpenAPI description and backend service, repository, integration, contract, and unit tests.
- Keep `FLAT` out of the persisted `ChunkKind` enum and leave chunk processing, persistence, embeddings, extraction, migrations, and the compatibility complete-list route unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `document-management`: Define the virtual `FLAT` bounded-page selector, mixed hierarchy behavior, validation, and equivalence between flat hierarchy counts and flat page totals.

## Impact

The change affects `DocumentController`, `DocumentProcessingService`, `DocumentChunkRepository`, the document/chunk service and Neo4j integration tests, and the OpenAPI contract test. It introduces no dependency or persisted data-format change. The behavior assumes legacy null or unsupported chunk kinds are deleted or reprocessed separately; those records are intentionally excluded from the canonical flat predicate.
