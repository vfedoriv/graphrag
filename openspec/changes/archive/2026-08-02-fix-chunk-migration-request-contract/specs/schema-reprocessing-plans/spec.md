## MODIFIED Requirements

### Requirement: Reprocessing plans are explicit post-activation resources
The system SHALL allow a client to create a durable schema-activation reprocessing plan only for a published schema that is currently active for the same knowledge base, selecting either all eligible documents with `allDocuments=true` or an explicit non-empty owned document set. For schema activation, omitted, `null`, and `false` `allDocuments` values SHALL mean that the all-documents choice was not selected.

#### Scenario: Create a plan for all documents
- **WHEN** a client requests a plan for all eligible documents after explicitly activating the published schema
- **THEN** the system snapshots the knowledge base, target schema identifier and content hash, active AI profile revision, and eligible document identifiers and SHA-256 values
- **AND** returns an accepted response with plan status and progress location

#### Scenario: Create a plan for explicit documents without all-documents choice
- **WHEN** a schema-activation request supplies a non-empty owned `documentIds` list and `allDocuments` is omitted, `null`, or `false`
- **THEN** the system selects the explicit document set

#### Scenario: Schema activation omits every document choice
- **WHEN** a schema-activation request does not select `allDocuments=true` and does not supply a non-empty `documentIds` list
- **THEN** the system rejects the request as invalid

#### Scenario: Schema activation combines document choices
- **WHEN** a schema-activation request supplies both `allDocuments=true` and a non-empty `documentIds` list
- **THEN** the system rejects the request as invalid

#### Scenario: Target schema is not active
- **WHEN** a client requests a plan for a schema that is associated but not currently active for the knowledge base
- **THEN** the system rejects plan creation as a conflict
- **AND** no document processing begins

#### Scenario: Selected document is not owned by the knowledge base
- **WHEN** an explicit plan contains a missing or foreign document identifier
- **THEN** the system rejects plan creation using established ownership-safe behavior
