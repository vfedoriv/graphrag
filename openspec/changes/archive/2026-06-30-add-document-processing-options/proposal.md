## Why

Document processing currently accepts only `allowOverwrite`, so parser and format choices are hidden behind backend defaults. As PDF, DOCX, OCR, layout, and metadata handling become user-visible retrieval quality controls, clients need a typed way to discover, save, override, and audit processing options.

## What Changes

- Add a document processing option catalog with typed definitions, defaults, applicability rules, constraints, and validation errors.
- Add document-scoped saved processing defaults managed through explicit API endpoints.
- Extend document processing requests to accept one-run option overrides while preserving the existing `allowOverwrite` query parameter.
- Persist a first-class processing run history with requested, saved-default, and effective options for each parse/embed/extract attempt.
- Include processing runs in document replacement and deletion cleanup.
- Do not implement page-aware PDF splitting or Tika-specific option mappings in this change; those are handled by `add-page-aware-tika-processing`.

## Capabilities

### New Capabilities
- `document-processing-options`: Typed discovery, validation, document defaults, and request-time overrides for document processing options.
- `document-processing-run-history`: Durable processing run records that capture effective options, lifecycle status, errors, and active completed result metadata.

### Modified Capabilities
- `document-management`: Replacement and deletion cleanup must include processing run records created for the target document.

## Impact

- Affected API: document processing endpoint, new document processing options/defaults endpoints, and response DTOs for option definitions and run metadata.
- Affected backend code: document controller, document processing service, parsing boundary, Neo4j domain/repositories, cleanup services, validation/error handling, and OpenAPI annotations.
- Affected data model: `DocumentUpload` stores saved option defaults; new `DocumentProcessingRun` nodes link to uploaded documents.
- No new external parser dependencies are expected in this change.
