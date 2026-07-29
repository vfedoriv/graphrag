## ADDED Requirements

### Requirement: Polyglot persistence invariants have integration coverage
The test suite SHALL verify shared-server database isolation, role identity, Flyway placement, transaction routing, store-specific rollback, cross-store recovery, graph scoping, and graph purity.

#### Scenario: Shared PostgreSQL isolation tests run
- **WHEN** GraphRAG provisioning, Flyway, and reset tests execute against a server containing seeded Langfuse data
- **THEN** all GraphRAG objects remain in its database/schema
- **AND** Langfuse sentinel data remains unchanged

#### Scenario: Transaction routing tests run
- **WHEN** relational and graph failure paths are exercised
- **THEN** rollback affects only the selected store
- **AND** the explicit SDN template supports repository queries

#### Scenario: Full-flow tests run
- **WHEN** the canonical test suite completes without external AI credentials
- **THEN** API behavior, recovery semantics, KB isolation, and absence of retired graph structures are verified
