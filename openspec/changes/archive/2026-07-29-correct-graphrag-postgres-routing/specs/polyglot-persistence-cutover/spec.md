## MODIFIED Requirements

### Requirement: Cutover resets only GraphRAG-owned state
The system SHALL provide a one-time reset procedure that discards and recreates GraphRAG's PostgreSQL database/schema and Neo4j data, removes the known GraphRAG-owned `langfuse.app` residue, and does not require preservation of existing GraphRAG data.

#### Scenario: GraphRAG is cut over
- **WHEN** an operator follows the documented reset and startup sequence
- **THEN** GraphRAG starts from empty managed stores and seeds required defaults
- **AND** the accidental GraphRAG-owned `langfuse.app` residue is removed
- **AND** Langfuse-owned tables are not deleted

#### Scenario: Existing GraphRAG data is present
- **WHEN** the one-time reset runs against old GraphRAG state
- **THEN** that state is intentionally discarded without backup or migration

### Requirement: Final startup is verified across both stores
The cutover procedure MUST use the normal application health and repository test workflow to confirm that the fresh GraphRAG topology starts after reset.

#### Scenario: Fresh-start smoke verification succeeds
- **WHEN** GraphRAG starts after empty PostgreSQL and Neo4j stores are provisioned
- **THEN** application health responds successfully
- **AND** the normal deterministic test suite passes without external AI credentials
- **AND** no recurring cutover verifier is required

## REMOVED Requirements

### Requirement: GraphRAG backups and restores are database-scoped

**Reason**: The current environment is explicitly disposable and the routing correction starts from empty GraphRAG stores, so backup and restore procedures add unnecessary migration complexity.

**Migration**: Discard existing GraphRAG state and follow the fresh-start reset procedure. Design a separate preservation workflow only if a future deployment requires retained data.
