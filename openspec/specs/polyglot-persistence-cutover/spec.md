# polyglot-persistence-cutover Specification

## Purpose
Define the safe reset-only cutover, backup, restore, and verification requirements for GraphRAG's final PostgreSQL and Neo4j persistence topology.

## Requirements

### Requirement: Cutover resets only GraphRAG-owned state
The system SHALL provide a reset-only cutover procedure that recreates GraphRAG's PostgreSQL database/schema and Neo4j data without deleting the shared PostgreSQL volume or modifying the Langfuse database.

#### Scenario: GraphRAG is cut over
- **WHEN** an operator follows the documented reset and startup sequence
- **THEN** GraphRAG starts from empty managed stores and seeds required defaults
- **AND** Langfuse data and health remain intact

#### Scenario: Existing GraphRAG data must be retained
- **WHEN** an operator requires recovery instead of discard
- **THEN** the procedure requires database-scoped backups before reset
- **AND** does not represent the cutover as an online migration

### Requirement: GraphRAG backups and restores are database-scoped
The system SHALL document and verify `pg_dump` and `pg_restore` operations that target only the `graphrag` database and preserve required ownership and schema placement.

#### Scenario: GraphRAG is restored
- **WHEN** an operator restores a valid GraphRAG database backup
- **THEN** the `langfuse` database is neither dropped nor overwritten

### Requirement: Final startup is verified across both stores
The cutover procedure MUST verify Flyway placement, datasource role, seed data, API health, graph indexes, graph purity, and Langfuse health.

#### Scenario: Smoke verification succeeds
- **WHEN** the final topology starts after reset
- **THEN** all relational, graph, API, and Langfuse isolation checks pass before the cutover is declared complete
