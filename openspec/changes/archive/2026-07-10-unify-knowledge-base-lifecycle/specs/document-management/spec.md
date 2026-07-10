## ADDED Requirements

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
