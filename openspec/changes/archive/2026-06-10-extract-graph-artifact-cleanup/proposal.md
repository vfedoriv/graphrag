## Why

Document-scoped graph cleanup is implemented as large direct Cypher blocks inside multiple orchestration services. This couples upload, replacement, deletion, and extraction-run completion logic to low-level graph cleanup details and makes cleanup behavior harder to evolve safely.

## What Changes

- Extract document and extraction-run cleanup Cypher into a dedicated graph artifact cleanup component.
- Keep cleanup semantics unchanged for document replacement, document deletion, failed run cleanup, completed run overwrite cleanup, graph relationship deletion, and obsolete extracted node deletion.
- Return explicit cleanup result counts from the cleanup component for logging and tests.
- Leave repository and API behavior unchanged.

## Capabilities

### New Capabilities
- `graph-artifact-cleanup`: Defines centralized cleanup of document-scoped graph artifacts and extraction-run artifacts.

### Modified Capabilities

## Impact

- Affected services: `DocumentUploadService`, `GraphExtractionService`.
- Affected graph operations: document chunk deletion, extraction run deletion, extracted relationship deletion, obsolete extracted node deletion.
- Existing integration tests for document replacement/deletion and extraction cleanup should continue to pass.
- No API contract changes are intended.
