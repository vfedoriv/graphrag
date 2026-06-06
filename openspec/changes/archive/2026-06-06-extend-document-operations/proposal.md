## Why

Document uploads currently support create, list, process, and chunk retrieval, but users cannot manage an existing document lifecycle from the API. This limits UI workflows that need to replace stale source files, remove documents from a knowledge base, or open the original file for context.

## What Changes

- Add document update support so an existing document can be replaced with a new uploaded file while preserving a stable document identity.
- Add document deletion support that removes the document record, stored binary, chunks, extraction runs, and extracted graph data tied to that document without affecting unrelated knowledge base data.
- Include local file path metadata in document responses so a trusted desktop UI can open the source document with the operating system's default application.
- Keep backend file-opening out of scope; the backend exposes metadata and the UI decides how to invoke the local OS.
- Return consistent RFC 7807 errors for missing documents, knowledge base mismatches, invalid uploads, storage failures, and deletion failures.

## Capabilities

### New Capabilities
- `document-management`: Document update, delete, and source-file context metadata behavior for uploaded knowledge base documents.

### Modified Capabilities

None.

## Impact

- Affected API: `DocumentController` document upload/list/process/chunks responses plus new update and delete document endpoints under `/api/v1`.
- Affected services: document upload/storage, document processing cleanup, graph extraction cleanup, and repository deletion paths.
- Affected persistence: document, chunk, extraction run, and extracted graph nodes/relationships associated with a document.
- Affected tests: controller, service, storage, and integration coverage for update/delete behavior and local file path response metadata.
- No new external dependencies are expected.
