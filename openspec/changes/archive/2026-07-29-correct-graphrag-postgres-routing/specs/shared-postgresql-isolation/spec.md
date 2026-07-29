## ADDED Requirements

### Requirement: Compose lifecycle cannot override GraphRAG datasource ownership
The system MUST prevent Spring Boot Docker Compose service-connection discovery from routing GraphRAG through Langfuse's PostgreSQL database or role while retaining supported local Compose lifecycle behavior.

#### Scenario: Local application starts with Compose support enabled
- **WHEN** the shared `langfuse-postgres` service is running and GraphRAG starts with default local datasource settings
- **THEN** GraphRAG uses the explicit datasource configured for database `graphrag`, role `graphrag`, and schema `app`

#### Scenario: Compose metadata describes Langfuse ownership
- **WHEN** Spring Boot inspects the Langfuse-owned PostgreSQL Compose service
- **THEN** that service is ignored for application connection-detail discovery
- **AND** its `langfuse / langfuse` bootstrap metadata does not replace GraphRAG's explicit datasource
