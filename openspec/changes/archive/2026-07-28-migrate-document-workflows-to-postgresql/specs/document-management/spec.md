## ADDED Requirements

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
