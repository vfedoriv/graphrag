## Context

The current processing flow reads the uploaded binary, calls `DocumentParsingService.parse(filename, contentType, bytes)`, receives a single text string, chunks it, embeds it, and starts graph extraction. Parser choices are internal to `RoutedDocumentParser`, and completed extraction runs are the only durable processing history.

The desired behavior has two distinct layers. This change establishes the API and data contract for options and run history. Parser-specific behavior, especially page-aware PDF/Tika processing, can then build on that contract without reshaping storage and public APIs again.

## Goals / Non-Goals

**Goals:**

- Provide a typed, discoverable processing option registry.
- Persist document-level saved defaults separately from upload identity and SHA-256 deduplication.
- Allow process requests to override saved defaults for a single run.
- Persist effective options and lifecycle state for every processing attempt.
- Keep existing processing behavior unchanged when no options are supplied.
- Include processing run records in document replacement and deletion cleanup.

**Non-Goals:**

- Implement rich Tika PDF/OCR mappings or page-aware parsing in this change.
- Add knowledge-base-level default options.
- Treat option values as runtime settings or arbitrary Spring environment values.
- Reprocess already processed documents automatically when defaults change.
- Store raw binary-derived content or extracted images in processing run records.

## Decisions

1. Add a dedicated processing option registry.

   The registry exposes option definitions with keys, labels, value type, defaults, constraints, parser/format applicability, mutability, and descriptions. Unknown keys and unsupported options for a document format are rejected.

   Rationale: this mirrors the runtime settings allowlist style and avoids unsafe raw parser passthrough. The alternative was accepting a free-form map of Tika/native keys, but that would create unstable API behavior and weak validation.

2. Store saved defaults on the document, not in upload dedup identity.

   `DocumentUpload` gains JSON defaults and an update timestamp. Upload deduplication remains based on knowledge base and SHA-256 only; changing defaults does not create, merge, or replace documents.

   Rationale: processing choices are mutable workflow preferences, not source-file identity. The alternative was storing defaults as separate nodes immediately, but a JSON field is enough for one document's current defaults and keeps lookup simple.

3. Use explicit defaults endpoints.

   Clients read applicable definitions and current defaults through `GET /api/v1/documents/{documentId}/processing-options`, replace defaults through `PUT /api/v1/documents/{documentId}/processing-options/defaults`, and clear them through `DELETE /api/v1/documents/{documentId}/processing-options/defaults`.

   Rationale: process requests should not unexpectedly mutate saved defaults. The alternative was "process saves defaults", which is convenient but surprising.

4. Extend process with an optional JSON body while preserving query compatibility.

   The process endpoint accepts `{ "allowOverwrite": false, "options": { ... } }`. Existing calls with only `?allowOverwrite=true` keep working. If both query and body provide `allowOverwrite` with different values, the request is rejected.

   Rationale: a request body is the natural place for structured options, but keeping the query parameter avoids breaking current clients and tests.

5. Persist processing runs separately from extraction runs.

   A `DocumentProcessingRun` spans parse, chunk, embedding, and graph extraction orchestration. It stores parser id, detected format, source SHA-256, requested options, saved defaults snapshot, effective options, status, timestamps, error message, and active-completed flag.

   Rationale: extraction runs start after chunks exist and do not capture parser/chunking decisions or parse failures. The alternative was adding latest-only fields to `DocumentUpload`, but that loses failed-run auditability and overwrite history.

6. Mark only one completed processing run active per document.

   Successful processing marks the new completed run active. When overwrite is allowed and succeeds, prior active completed processing runs become inactive. Failed attempts do not deactivate the previous active completed result.

   Rationale: clients can audit history while retrieval continues to reflect the latest successful artifacts.

## Risks / Trade-offs

- [Risk] Two histories, processing runs and extraction runs, can confuse clients. -> Mitigation: processing runs represent end-to-end orchestration; extraction runs remain graph-extraction-specific and are linked or referenced from processing run metadata where practical.
- [Risk] JSON defaults can drift from registry definitions. -> Mitigation: validate stored defaults on read/process, ignore nothing silently, and fail with a clear validation error if stale keys are no longer applicable.
- [Risk] Adding a request body to an existing endpoint can create ambiguous `allowOverwrite` behavior. -> Mitigation: define query/body precedence by rejecting conflicts and accepting either form alone.
- [Risk] Processing run cleanup can delete too broadly. -> Mitigation: cleanup queries remain scoped by document id and knowledge base checks already used for destructive document operations.

## Migration Plan

1. Add DTOs, registry, validation service, and default merge logic with no behavior change for empty options.
2. Add document defaults fields and processing run nodes/repositories.
3. Extend the process endpoint and service signatures while keeping existing overloads for internal callers.
4. Add cleanup of processing runs to replacement/delete paths.
5. Add tests for registry validation, defaults management, process compatibility, processing run lifecycle, and cleanup.

Rollback is to remove the new endpoints, body handling, defaults fields, and processing run nodes. Existing document upload, chunk, extraction, and query behavior remains compatible because source binary identity and chunk storage do not change.

## Open Questions

- Should the processing run list/detail endpoint be added in this change, or should run records remain backend/audit data until a UI needs explicit history browsing?
