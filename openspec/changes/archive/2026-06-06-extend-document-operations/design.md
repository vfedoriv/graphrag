## Context

The document API currently supports uploading a multipart file to a knowledge base, listing uploaded documents, processing a document, and listing generated chunks. Stored binaries are written by `BinaryStorageService`, and the local implementation can resolve a stored `file://` URI back to an absolute `Path`.

Processed document state spans multiple parts of the system: the `DocumentUpload` node, stored binary, `DocumentChunk` nodes, extraction run nodes, extracted graph relationships, and extracted graph nodes tagged with document provenance. Update and delete operations therefore need document-scoped cleanup rather than a simple repository save/delete.

## Goals / Non-Goals

**Goals:**
- Allow a document's binary and metadata to be replaced while preserving the document id.
- Allow a document and all document-owned derived artifacts to be deleted from a knowledge base.
- Add `localPath` metadata to document responses so a trusted desktop UI can open the source file with the OS default application.
- Keep all cleanup scoped to the target document and knowledge base.
- Preserve existing upload/list/process/chunk behavior unless explicitly changed by the new contract.

**Non-Goals:**
- The backend will not launch local OS programs or expose an endpoint that opens files.
- The backend will not stream or download document binaries as part of this change.
- This change will not introduce soft deletes, document version history, or recycle-bin behavior.
- This change will not change schema activation, extraction semantics, or query execution behavior.

## Decisions

1. Use knowledge-base scoped document management endpoints.

   Add `PUT /api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}` for replacement upload and `DELETE /api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}` for deletion. Existing `/api/v1/documents/{documentId}/process` and chunk endpoints can remain as-is.

   Rationale: update and delete are more destructive than process/chunk reads, so scoping them by knowledge base gives callers an extra guardrail and enables explicit knowledge base mismatch errors. The alternative was `/api/v1/documents/{documentId}` only, but that makes accidental cross-knowledge-base operations harder to detect.

2. Treat update as replacement of the source document with stable identity.

   The update endpoint accepts multipart `file`, stores the replacement bytes under the same document id, updates filename/content type/size/hash/content URI, resets status to `UPLOADED`, clears `processedAt` and `errorMessage`, and removes previously derived chunks/extraction artifacts.

   Rationale: preserving identity keeps existing UI selection state and document references stable, while clearing derived artifacts prevents stale chunks or graph data from representing old content. The alternative was delete-and-create, but that changes the document id and complicates UI workflows.

3. Return an absolute `localPath` in document responses.

   Extend `DocumentUploadResponse` with a nullable `localPath` derived from `BinaryStorageService.resolvePath(URI.create(contentUri))` when a stored URI is available. `contentUri` remains for storage identity; `localPath` is the value a trusted local UI can pass to its OS integration.

   Rationale: this cleanly separates storage address from UI open-context metadata. The alternative was to expose an "open document" backend endpoint, but launching OS programs from the backend is environment-specific, hard to secure, and unnecessary for a UI running on the same machine.

4. Add explicit document-scoped cleanup service behavior.

   Implement reusable cleanup for a document's derived artifacts: delete chunks, remove extraction runs and their relationships, remove extracted graph relationships with matching document provenance, and remove extracted graph nodes for the target document that are no longer retained by any extraction run. Delete the stored binary when the document is deleted, and delete the prior binary after a successful replacement write.

   Rationale: update and delete need stronger cleanup than existing post-success extraction cleanup because they remove all artifacts for a document, not just stale failed/overwritten runs. Keeping this logic in a service method makes controller behavior straightforward and testable.

5. Reject replacement that would duplicate another document in the same knowledge base.

   If the replacement file has the same SHA-256 as a different document in the same knowledge base, return a conflict instead of merging documents or changing the target id to point at another record.

   Rationale: upload deduplication can return an existing record for create, but update promises stable identity. Allowing two document ids to represent the same content weakens deduplication; returning another id from an update would violate the replacement contract.

## Risks / Trade-offs

- Stored binary deletion can fail after database cleanup -> Mitigate by ordering operations so replacement writes happen before database changes, by logging storage cleanup failures with document id and path/URI, and by returning an error when the delete operation cannot remove the primary stored binary.
- Neo4j cleanup queries could delete too broadly -> Mitigate with document id and knowledge base checks plus exclusions for infrastructure labels already used in extraction cleanup.
- Absolute local paths expose host filesystem details -> Mitigate by documenting `localPath` as trusted-local-UI metadata and deriving it only from configured document storage, not arbitrary request input.
- Update removes processed graph state immediately -> Mitigate by resetting status to `UPLOADED` so clients know reprocessing is required before query results reflect the new source document.
- Concurrent update/process/delete requests can race -> Mitigate with transactional database updates, optimistic locking on `DocumentUploadNode`, and tests for expected conflict/error behavior where practical.
