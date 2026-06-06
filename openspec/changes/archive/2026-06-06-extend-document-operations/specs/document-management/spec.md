## ADDED Requirements

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
- **WHEN** a processed document with chunks, extraction runs, and extracted graph data is successfully replaced
- **THEN** the system deletes chunks for that document
- **AND** the system deletes extraction runs for that document
- **AND** the system deletes extracted graph relationships for that document
- **AND** the system deletes extracted graph nodes for that document that are not retained by any remaining extraction run

#### Scenario: Replacement storage write fails
- **WHEN** replacement binary storage fails before the replacement metadata is saved
- **THEN** the system returns an error
- **AND** the existing document metadata, stored binary, chunks, extraction runs, and extracted graph data remain unchanged

### Requirement: Document can be deleted
The system SHALL allow an existing document to be deleted from its knowledge base.

#### Scenario: Successful document deletion
- **WHEN** a client deletes an existing document id within the document's knowledge base
- **THEN** the system removes the document record
- **AND** the system removes the stored binary for that document
- **AND** the system removes chunks for that document
- **AND** the system removes extraction runs for that document
- **AND** the system removes extracted graph relationships for that document
- **AND** the system removes extracted graph nodes for that document that are not retained by any remaining extraction run
- **AND** the response indicates successful deletion without returning document content

#### Scenario: Deleted document is not found
- **WHEN** a client deletes a document id that does not exist
- **THEN** the system rejects the request with a not found error

#### Scenario: Deleted document belongs to another knowledge base
- **WHEN** a client deletes a document id that exists under a different knowledge base
- **THEN** the system rejects the request and does not delete the document, stored binary, chunks, extraction runs, or extracted graph data

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
