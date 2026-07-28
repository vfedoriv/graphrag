# persistence-transaction-routing Specification

## Purpose
Define explicit ownership, repository wiring, and transaction routing for the PostgreSQL and Neo4j persistence stacks.

## Requirements

### Requirement: Each repository stack has an explicit transaction manager
The system SHALL bind relational repositories to the primary JPA transaction manager named `transactionManager` and graph repositories to `neo4jTransactionManager` through an explicitly transaction-aware `Neo4jTemplate`.

#### Scenario: Persistence beans are inspected
- **WHEN** the application context starts with both persistence stacks
- **THEN** `transactionManager` is the primary JPA transaction manager
- **AND** `neo4jTransactionManager` manages Neo4j operations
- **AND** an SDN repository query executes through the configured Neo4j template

### Requirement: Persistence transactions are store-qualified
The system MUST use store-qualified transaction annotations for persistence-aware operations and MUST NOT imply one atomic transaction across PostgreSQL and Neo4j.

#### Scenario: Relational work rolls back
- **WHEN** a relational transactional operation fails
- **THEN** its PostgreSQL changes roll back
- **AND** no Neo4j transaction is enlisted

#### Scenario: Graph work rolls back
- **WHEN** a graph transactional operation fails
- **THEN** its Neo4j changes roll back
- **AND** no PostgreSQL transaction is enlisted

#### Scenario: Cross-store workflow executes
- **WHEN** a workflow changes PostgreSQL state and performs Neo4j or filesystem work
- **THEN** an unannotated orchestrator invokes separately proxied transactional collaborators
- **AND** recovery relies on committed checkpoints rather than XA or a chained transaction manager

### Requirement: Persistence packages remain disjoint
The system SHALL keep JPA entities and repositories separate from Neo4j nodes and repositories and SHALL reject raw or unqualified persistence transactions through architecture verification.

#### Scenario: Architecture rules run
- **WHEN** the architecture test suite scans persistence-aware classes
- **THEN** mixed repository ownership and raw or unqualified transactional annotations fail the test
