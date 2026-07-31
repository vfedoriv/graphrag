# document-processing-options Specification

## Purpose
TBD - created by archiving change add-document-processing-options. Update Purpose after archive.
## Requirements
### Requirement: Processing option catalog is discoverable
The system SHALL expose typed processing option definitions applicable to an uploaded document's detected parser and file format.

#### Scenario: Client reads applicable processing options
- **WHEN** a client requests processing options for an uploaded document
- **THEN** the response includes the detected parser identifier and file format
- **AND** the response includes each applicable option with key, value type, default value, current saved default value when present, constraints, and description
- **AND** options that are not applicable to the uploaded document's parser or file format are not reported as applicable

#### Scenario: Unsupported document is queried for options
- **WHEN** a client requests processing options for a document whose type is not supported for processing
- **THEN** the system rejects the request with a validation error that identifies the unsupported document type

### Requirement: Document processing defaults are managed explicitly
The system SHALL allow clients to save and clear validated document-scoped processing defaults without changing the uploaded source document identity.

#### Scenario: Client saves valid document defaults
- **WHEN** a client replaces saved processing defaults for a document with valid option values
- **THEN** the system persists those defaults on the document
- **AND** subsequent processing option reads return the saved defaults
- **AND** the document filename, content type, size, SHA-256 hash, content URI, status, chunks, extraction runs, and extracted graph data remain unchanged

#### Scenario: Client saves invalid document defaults
- **WHEN** a client replaces saved processing defaults with an unknown option, invalid value type, constraint violation, or option unsupported by the document format
- **THEN** the system rejects the request with a validation error
- **AND** previously saved defaults remain unchanged

#### Scenario: Client clears document defaults
- **WHEN** a client clears saved processing defaults for a document
- **THEN** the system removes the saved defaults
- **AND** subsequent processing uses built-in defaults unless a process request supplies overrides

### Requirement: Processing requests accept one-run option overrides
The system SHALL allow document processing requests to supply validated option overrides for the current run without mutating saved document defaults.

#### Scenario: Client processes with request overrides
- **WHEN** a client processes a document with valid processing option overrides
- **THEN** the system merges built-in defaults, saved document defaults, and request overrides into effective options
- **AND** request overrides take precedence over saved document defaults
- **AND** saved document defaults are not changed by the processing request

#### Scenario: Client processes without request overrides
- **WHEN** a client processes a document without processing option overrides
- **THEN** the system uses built-in defaults plus saved document defaults as the effective options

#### Scenario: Process request contains invalid overrides
- **WHEN** a client processes a document with an unknown option, invalid value type, constraint violation, or option unsupported by the document format
- **THEN** the system rejects the request before parsing starts
- **AND** no chunks, embeddings, extraction runs, extracted graph data, or processing run side effects are created

### Requirement: Processing request remains backward compatible
The system SHALL preserve existing document processing request behavior while adding structured processing options.

#### Scenario: Existing allowOverwrite query request is used
- **WHEN** a client processes a document using the existing `allowOverwrite` query parameter and no request body
- **THEN** the system processes the document with the same overwrite semantics as before
- **AND** the system uses built-in defaults plus any saved document defaults as effective processing options

#### Scenario: Body allowOverwrite is used
- **WHEN** a client processes a document with a request body that includes `allowOverwrite`
- **THEN** the system uses the body value when the query parameter is absent

#### Scenario: Query and body allowOverwrite conflict
- **WHEN** a client supplies `allowOverwrite` in both the query parameter and request body with different values
- **THEN** the system rejects the request with a validation error
- **AND** document processing does not start

### Requirement: Immutable-plan overwrite processing
Document overwrite processing invoked by a migration worker SHALL accept the validated immutable plan snapshot and SHALL not resolve behavior-affecting chunk, tokenizer, profile, embedding-space, schema, or processing options from later live state.

#### Scenario: Worker processes current target
- **WHEN** a worker claims an item whose source and target remain current
- **THEN** overwrite processing uses the plan snapshot and commits that document independently
