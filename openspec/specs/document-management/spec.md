# document-management Specification

## Purpose
Define update, delete, and source-file context behavior for uploaded knowledge base documents.
## Requirements
### Requirement: Document responses expose local source path
The system SHALL include source-file context metadata in document responses so trusted local clients can open the stored document outside the backend.

#### Scenario: Uploaded document response includes local path
- **WHEN** a document upload stores the binary in local filesystem storage
- **THEN** the response includes the existing `contentUri`
- **AND** the response includes `localPath` as the absolute normalized filesystem path resolved from the stored content URI

#### Scenario: Listed document response includes local path
- **WHEN** documents are listed for a knowledge base
- **THEN** each document response with a stored content URI includes `localPath` as the absolute normalized filesystem path resolved from that URI

#### Scenario: Processed document response includes local path
- **WHEN** a document processing request returns a document response
- **THEN** the response includes `localPath` for the processed document when the stored content URI can be resolved to a local filesystem path

### Requirement: Document can be updated by replacement upload
The system SHALL allow an existing document in a knowledge base to be replaced with a new multipart file while preserving the document identifier.

#### Scenario: Successful document replacement
- **WHEN** a client sends a replacement file for an existing document id within the document's knowledge base
- **THEN** the system stores the replacement binary for that document id
- **AND** the document response keeps the same document id and knowledge base id
- **AND** the document metadata reflects the replacement filename, content type, byte size, SHA-256 hash, content URI, and local path
- **AND** the document status is `UPLOADED`
- **AND** `processedAt` and `errorMessage` are cleared

#### Scenario: Replacement document is not found
- **WHEN** a client sends a replacement file for a document id that does not exist
- **THEN** the system rejects the request with a not found error

#### Scenario: Replacement document belongs to another knowledge base
- **WHEN** a client sends a replacement file for a document id that exists under a different knowledge base
- **THEN** the system rejects the request and does not alter the document or stored binary

#### Scenario: Replacement duplicates another document
- **WHEN** a client sends a replacement file whose SHA-256 hash matches a different document in the same knowledge base
- **THEN** the system rejects the request with a conflict error
- **AND** the target document metadata and derived artifacts remain unchanged

#### Scenario: Replacement upload is invalid
- **WHEN** a client sends an invalid multipart payload for document replacement
- **THEN** the system rejects the request with a validation error
- **AND** the existing document metadata, stored binary, chunks, extraction runs, and extracted graph data remain unchanged

### Requirement: Document replacement clears derived artifacts
The system MUST remove stale derived artifacts for a document when its source binary is successfully replaced.

#### Scenario: Replacement clears chunks and extraction artifacts
- **WHEN** a processed document with chunks, processing runs, extraction runs, and extracted graph data is successfully replaced
- **THEN** the system deletes chunks for that document
- **AND** the system deletes processing runs for that document
- **AND** the system deletes extraction runs for that document
- **AND** the system deletes extracted graph relationships for that document
- **AND** the system deletes extracted graph nodes for that document that are not retained by any remaining extraction run

#### Scenario: Replacement storage write fails
- **WHEN** replacement binary storage fails before the replacement metadata is saved
- **THEN** the system returns an error
- **AND** the existing document metadata, stored binary, chunks, processing runs, extraction runs, and extracted graph data remain unchanged

### Requirement: Document can be deleted
The system SHALL allow an existing document to be deleted from its knowledge base.

#### Scenario: Successful document deletion
- **WHEN** a client deletes an existing document id within the document's knowledge base
- **THEN** the system removes the document record
- **AND** the system removes the stored binary for that document
- **AND** the system removes chunks for that document
- **AND** the system removes processing runs for that document
- **AND** the system removes extraction runs for that document
- **AND** the system removes extracted graph relationships for that document
- **AND** the system removes extracted graph nodes for that document that are not retained by any remaining extraction run
- **AND** the response indicates successful deletion without returning document content

#### Scenario: Deleted document is not found
- **WHEN** a client deletes a document id that does not exist
- **THEN** the system rejects the request with a not found error

#### Scenario: Deleted document belongs to another knowledge base
- **WHEN** a client deletes a document id that exists under a different knowledge base
- **THEN** the system rejects the request and does not delete the document, stored binary, chunks, processing runs, extraction runs, or extracted graph data

#### Scenario: Stored binary deletion fails
- **WHEN** the system cannot delete the primary stored binary for a delete request
- **THEN** the system returns an error
- **AND** the document is not reported as successfully deleted

### Requirement: Document cleanup is scoped to the target document
The system MUST scope update and delete cleanup to the target document and MUST NOT remove unrelated knowledge base data or repository infrastructure nodes.

#### Scenario: Cleanup does not affect other documents
- **WHEN** a document is replaced or deleted
- **THEN** documents with different document ids keep their document records, stored binaries, chunks, extraction runs, and extracted graph data

#### Scenario: Cleanup does not remove infrastructure nodes
- **WHEN** update or delete cleanup removes extracted graph artifacts for a document
- **THEN** the cleanup does not delete knowledge base nodes, schema nodes, document upload nodes for other documents, document chunk nodes for other documents, or extraction run nodes for other documents

### Requirement: Document operations require managed knowledge-base ownership
The system SHALL accept document upload, replacement, deletion, processing, and listing only for a managed knowledge base.

#### Scenario: Upload targets missing knowledge base
- **WHEN** a client uploads a document for a knowledge-base identifier that has not been provisioned
- **THEN** the system rejects the request with not found
- **AND** it does not persist document metadata or a binary

#### Scenario: Listing targets missing knowledge base
- **WHEN** a client lists documents for a missing knowledge base
- **THEN** the system rejects the request with not found

### Requirement: Document mutations recover from cross-store failure
The system SHALL leave document metadata and binary storage in a reconciled state after upload, replacement, or deletion succeeds, fails, or is interrupted.

#### Scenario: Replacement is interrupted after new binary store
- **WHEN** replacement storage succeeds but the document metadata update is interrupted
- **THEN** reconciliation removes or completes the uncommitted replacement without losing the prior committed document binary

### Requirement: Document metadata is relational operational state
The system SHALL persist document identity, knowledge-base ownership, binary metadata, digest, storage location, and optimistic version in PostgreSQL while retaining binary content in the configured filesystem.

#### Scenario: A document is uploaded
- **WHEN** a valid binary is uploaded to a knowledge base
- **THEN** the binary and relational metadata are reconciled
- **AND** duplicate SHA-256 content within that knowledge base is rejected

#### Scenario: The same digest exists in another knowledge base
- **WHEN** a document with the same SHA-256 is uploaded to a different knowledge base
- **THEN** the upload is evaluated independently within the target knowledge base

### Requirement: Replacement and deletion retain durable cleanup intent
The system MUST preserve retryable relational mutation records until filesystem and graph artifacts match the committed document outcome.

#### Scenario: Replacement is interrupted after binary storage
- **WHEN** the new binary is stored but metadata replacement does not complete
- **THEN** reconciliation can complete or reverse the mutation from the durable relational journal

#### Scenario: Deletion is interrupted
- **WHEN** relational intent commits but a filesystem or graph cleanup step fails
- **THEN** the document remains recoverably pending
- **AND** unrelated document and knowledge-base data remain unchanged

### Requirement: Rich flat chunk reads
Document chunk reads SHALL return authoritative child source text and queryable provenance including chunk kind, section/page order, source range when reliable, structural path, token count, strategy revision, and tokenizer identity without returning the synthetic embedding header as document text.

#### Scenario: Client lists recursive chunks
- **WHEN** a client retrieves chunks for an owned recursively processed document
- **THEN** chunks are in deterministic document order and expose exact source provenance plus revision metadata

### Requirement: Hierarchical chunk reads
Document chunk reads SHALL distinguish `PARENT` and `CHILD`, expose parent identity and bounded source/page/structural scope, and return chunks in deterministic hierarchy and document order.

#### Scenario: Client reads hierarchy
- **WHEN** a client lists chunks for a hierarchically processed owned document
- **THEN** the response makes child-to-parent membership explicit without exposing parent embedding fields
