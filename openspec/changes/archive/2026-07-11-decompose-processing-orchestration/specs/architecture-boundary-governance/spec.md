## ADDED Requirements

### Requirement: Workflow and infrastructure adapter boundaries are enforced
Architecture tests SHALL constrain application workflow components to orchestration and named infrastructure adapters to direct repository, Neo4j, filesystem, or model-client integration.

For document processing, runtime settings, and schema generation, direct infrastructure calls introduced or migrated by this change SHALL reside in the governed infrastructure adapter packages. Unrelated legacy direct Neo4j users SHALL remain explicit frozen exceptions and SHALL NOT be treated as authorization for new direct access.

#### Scenario: Direct infrastructure dependency is introduced
- **WHEN** a workflow component introduces a direct `Neo4jClient`, filesystem, or provider-client dependency outside an approved adapter boundary
- **THEN** the architecture test fails with the originating class and forbidden dependency

#### Scenario: Persistence adapter is introduced
- **WHEN** a named persistence adapter requires direct `Neo4jClient` access
- **THEN** architecture governance permits the dependency through a package-level rule or explicit named exception
- **AND** the exception remains visible to review
