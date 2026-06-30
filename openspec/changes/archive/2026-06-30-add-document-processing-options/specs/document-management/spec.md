## MODIFIED Requirements

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
