## 1. Option Registry And DTOs

- [x] 1.1 Add processing option definition, value type, constraint, applicability, defaults, and validation models.
- [x] 1.2 Implement `DocumentProcessingOptionsRegistry` with current TXT, PDF, and DOCX format entries and conservative built-in defaults.
- [x] 1.3 Add request/response DTOs for option discovery, saved defaults updates, and process request bodies.
- [x] 1.4 Add validation errors for unknown keys, invalid types, constraint violations, and unsupported options for the detected document format.

## 2. Document Defaults API

- [x] 2.1 Add `DocumentUpload` fields for saved processing defaults JSON and updated timestamp.
- [x] 2.2 Add service methods to read applicable definitions, replace saved defaults, clear saved defaults, and merge built-in defaults with saved defaults.
- [x] 2.3 Add `GET`, `PUT`, and `DELETE` document processing options/defaults endpoints.
- [x] 2.4 Ensure document defaults changes do not alter upload deduplication, SHA-256 identity, document status, chunks, or extraction artifacts.

## 3. Process Request And Run History

- [x] 3.1 Extend the process endpoint to accept an optional body with `allowOverwrite` and `options` while preserving the existing query parameter.
- [x] 3.2 Reject conflicting query/body `allowOverwrite` values and invalid option payloads before processing starts.
- [x] 3.3 Add `DocumentProcessingRun` domain and repository support with requested, saved-default, and effective option JSON snapshots.
- [x] 3.4 Create and update processing runs across parse, chunk, embed, extraction, completion, and failure states.
- [x] 3.5 Keep the previous active completed processing run active when a new processing attempt fails.
- [x] 3.6 Mark previous active completed processing runs inactive after a successful overwrite.

## 4. Cleanup Integration

- [x] 4.1 Include document processing runs in replacement cleanup after a successful replacement binary write.
- [x] 4.2 Include document processing runs in delete cleanup for the target document.
- [x] 4.3 Keep cleanup scoped so other documents, knowledge bases, chunks, extraction runs, and graph artifacts are unaffected.

## 5. Tests And Verification

- [x] 5.1 Add registry unit tests for applicability, merge precedence, defaults, and validation failures.
- [x] 5.2 Add controller tests for option discovery, defaults save/clear, process body options, and `allowOverwrite` compatibility.
- [x] 5.3 Add service tests for processing run lifecycle, effective option persistence, failure behavior, and successful overwrite activation.
- [x] 5.4 Add integration coverage for replacement/delete cleanup of processing runs.
- [x] 5.5 Run focused document processing tests and `./mvnw test`.
