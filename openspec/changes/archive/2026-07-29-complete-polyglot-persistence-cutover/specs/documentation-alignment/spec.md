## ADDED Requirements

### Requirement: Contributor and operator guidance reflects the final persistence topology
The system documentation SHALL consistently describe PostgreSQL as required operational storage, Neo4j as graph-only storage, shared-server isolation, supported profiles, provisioning, startup, reset, backup, restore, and monitoring commands.

#### Scenario: Shared guidance is updated
- **WHEN** persistence cutover documentation is changed
- **THEN** overlapping facts in `README.md`, `AGENTS.md`, and `CLAUDE.md` are synchronized in the same change
- **AND** commands use the Maven Wrapper and safe database-scoped operations

#### Scenario: Destructive operations are documented
- **WHEN** the reset procedure names resources to recreate
- **THEN** it explicitly prohibits deleting the shared PostgreSQL volume or dropping the `langfuse` database
