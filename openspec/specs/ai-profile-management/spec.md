# ai-profile-management Specification

## Purpose
TBD - created by archiving change add-runtime-settings-ai-profiles. Update Purpose after archive.
## Requirements
### Requirement: AI provider profiles are managed as persisted application data
The system SHALL allow clients to create, list, retrieve, update, and delete OpenAI-compatible AI provider profiles persisted in Neo4j.

#### Scenario: Client creates an AI profile
- **WHEN** a client submits a profile with name, base URL, API key, chat model, embedding model, embedding dimensions, and optional timeout or retry settings
- **THEN** the system validates and persists the profile
- **AND** the profile can be returned by subsequent list and get requests

#### Scenario: Client submits invalid AI profile data
- **WHEN** a client submits a profile with missing required fields, invalid URL, non-positive embedding dimensions, or invalid timeout or retry values
- **THEN** the system rejects the request with a validation error
- **AND** no invalid profile is persisted

### Requirement: AI profile secrets are write-only in API responses
The system SHALL accept API key values for AI profiles without returning secret values in read responses.

#### Scenario: Client reads a profile with an API key
- **WHEN** a client lists or retrieves an AI profile that has an API key
- **THEN** the response indicates that an API key is configured
- **AND** the response does not include the raw API key value

#### Scenario: Client updates a profile without an API key field
- **WHEN** a client updates non-secret fields on an existing AI profile and omits the API key value
- **THEN** the existing stored API key remains unchanged

### Requirement: Default AI profile is seeded from startup configuration
The system SHALL seed an initial OpenAI-compatible AI profile from startup model configuration when no default profile exists.

#### Scenario: Application starts without a persisted default profile
- **WHEN** the application starts and no default AI profile exists
- **THEN** it creates a default profile using the configured base URL, API key, chat model, embedding model, and embedding dimensions

#### Scenario: Application starts with an existing default profile
- **WHEN** the application starts and a default AI profile already exists
- **THEN** it preserves the persisted profile values
- **AND** startup properties do not overwrite user-managed profile changes

### Requirement: Knowledge bases select active AI profiles
The system SHALL let each knowledge base have an active AI provider profile used by knowledge-base-scoped AI workflows. Every supported knowledge-base provisioning path SHALL assign the default AI profile unless another profile is explicitly assigned by supported API behavior.

#### Scenario: Client creates a knowledge base
- **WHEN** a client creates a knowledge base
- **THEN** the knowledge base is assigned the default AI profile unless another profile is explicitly assigned by supported API behavior

#### Scenario: Schema activation creates a knowledge base
- **WHEN** schema activation creates a previously missing knowledge base
- **THEN** the knowledge base is assigned the default AI profile before knowledge-base-scoped AI workflows can run

#### Scenario: Client assigns a profile to a knowledge base
- **WHEN** a client assigns an existing AI profile to a knowledge base
- **THEN** subsequent knowledge-base-scoped AI workflows resolve that profile for chat and embedding calls

#### Scenario: Assigned profile does not exist
- **WHEN** a client assigns a missing or deleted AI profile to a knowledge base
- **THEN** the system rejects the assignment

### Requirement: Incompatible embedding profile activation is blocked
The system SHALL prevent a knowledge base from activating an AI profile whose embedding space is incompatible with existing processed embeddings for that knowledge base. The system SHALL also prevent updating a profile when the update would make the embedding space incompatible with processed embeddings in any knowledge base assigned to that profile.

#### Scenario: Knowledge base has no processed embeddings
- **WHEN** a client assigns an AI profile to a knowledge base with no processed document embeddings
- **THEN** the system accepts the assignment if the profile is otherwise valid

#### Scenario: Knowledge base has compatible embeddings
- **WHEN** a client assigns an AI profile whose embedding provider endpoint, model, and dimensions match the knowledge base processed embeddings
- **THEN** the system accepts the assignment

#### Scenario: Knowledge base has incompatible embeddings
- **WHEN** a client assigns an AI profile whose embedding provider endpoint, model, or dimensions differ from the knowledge base processed embeddings
- **THEN** the system rejects the assignment with a clear compatibility error
- **AND** the previous active profile remains unchanged

#### Scenario: Shared profile update is incompatible
- **WHEN** a client updates an AI profile that is assigned to one or more knowledge bases with incompatible processed embeddings
- **THEN** the system rejects the profile update before persistence
- **AND** the profile revision and all knowledge-base assignments remain unchanged

### Requirement: Knowledge-base AI workflows use active profiles
The system SHALL resolve chat and embedding clients from the active profile of the target knowledge base for knowledge-base-scoped AI workflows.

#### Scenario: Document processing runs
- **WHEN** document processing embeds chunks or extracts graph data for a knowledge base
- **THEN** the embedding and chat calls use the knowledge base active AI profile

#### Scenario: Query generation or ask runs
- **WHEN** Cypher generation or ask executes for a knowledge base
- **THEN** chat calls use the knowledge base active AI profile

#### Scenario: Active profile is missing at runtime
- **WHEN** a knowledge-base-scoped AI workflow cannot resolve an active AI profile
- **THEN** the system fails the request with a clear configuration error
