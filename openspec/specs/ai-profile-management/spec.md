# ai-profile-management Specification

## Purpose
TBD - created by archiving change add-runtime-settings-ai-profiles. Update Purpose after archive.
## Requirements
### Requirement: AI provider profiles are managed as persisted application data
The system SHALL allow clients to create, list, retrieve, update, and delete OpenAI-compatible AI provider profiles persisted in PostgreSQL.

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

### Requirement: Knowledge-base profile assignment is relational and compatibility-safe
The system SHALL update a knowledge base's AI profile association in PostgreSQL only after validating embedding model and dimension compatibility with existing chunks.

#### Scenario: An incompatible profile is assigned
- **WHEN** chunks exist and the requested profile changes the effective embedding space incompatibly
- **THEN** the assignment is rejected
- **AND** the previous relational profile association remains unchanged

#### Scenario: A compatible profile is assigned
- **WHEN** the requested profile is compatible with the knowledge base's existing embedding space
- **THEN** the relational association commits atomically

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

### Requirement: PostgreSQL is authoritative for AI profiles
The system SHALL persist AI profile identity, provider configuration, default selection, secret state, and optimistic version in PostgreSQL without changing existing profile API contracts.

#### Scenario: A profile is created
- **WHEN** a valid profile request is submitted
- **THEN** the profile is committed relationally with its assigned identifier
- **AND** subsequent reads return no API-key value

#### Scenario: Concurrent profile mutation occurs
- **WHEN** a caller writes using a stale profile version
- **THEN** the relational update is rejected as a conflict
- **AND** the stored profile remains unchanged

### Requirement: Exactly one default profile is selected safely
The system SHALL enforce at most one default AI profile through a database constraint and SHALL seed a configured default idempotently when none exists.

#### Scenario: Concurrent default creation occurs
- **WHEN** concurrent startup or API operations attempt to create different default profiles
- **THEN** the database prevents multiple defaults
- **AND** the service resolves the surviving default deterministically

### Requirement: Typed profile tokenizer selection
The system SHALL allow an AI profile to declare an optional supported `tokenizerId`, include it in profile revision semantics, and return the non-secret configured or resolved tokenizer identity through profile reads.

#### Scenario: Compatible model alias
- **WHEN** an operator saves a supported explicit tokenizer for an embedding-model alias
- **THEN** subsequent processing resolves that tokenizer from the saved profile revision

#### Scenario: Invalid tokenizer update
- **WHEN** an operator submits an unsupported tokenizer identifier
- **THEN** the API returns a validation problem and leaves the prior profile revision unchanged

### Requirement: AI profiles declare a non-secret structured-output mode
The system SHALL accept and persist an optional `structuredOutputMode` on AI profiles with supported values `PORTABLE` and `NATIVE_JSON_SCHEMA`. New profiles, seeded default profiles, and migrated existing profiles SHALL use `PORTABLE` when no mode is configured. Profile reads and knowledge-base profile configuration reads SHALL expose the effective non-secret mode without exposing API keys. Selecting native mode SHALL be an explicit operator choice and SHALL NOT trigger automatic provider probing during profile persistence.

#### Scenario: Existing client creates a profile without a mode
- **WHEN** a valid create request omits `structuredOutputMode` or supplies null
- **THEN** the saved profile uses `PORTABLE`
- **AND** subsequent profile reads expose `PORTABLE`

#### Scenario: Existing database is migrated
- **WHEN** the application migrates profile rows created before structured-output mode was introduced
- **THEN** those profiles use `PORTABLE`
- **AND** their secrets, identities, existing revisions, default selection, and knowledge-base assignments remain unchanged

#### Scenario: Startup seeds the default profile
- **WHEN** startup seeds a previously absent default AI profile
- **THEN** the seeded structured-output mode is `PORTABLE`
- **AND** startup does not overwrite the mode of an already persisted default profile

#### Scenario: Operator enables native output
- **WHEN** a valid profile create or update request explicitly selects `NATIVE_JSON_SCHEMA`
- **THEN** the mode is persisted and exposed through non-secret configuration reads
- **AND** the save operation does not call the provider to test support
- **AND** API keys remain write-only

#### Scenario: Unsupported mode is submitted
- **WHEN** a profile request contains an unsupported structured-output mode value
- **THEN** the request fails with the existing validation problem envelope
- **AND** no profile values, revision, default selection, or assignments change

### Requirement: Structured-output mode follows profile revision and compatibility semantics
The system SHALL include structured-output mode in profile revision and model-cache invalidation behavior. An update omitting the mode or supplying null SHALL retain the saved mode; an explicit `PORTABLE` update SHALL disable native output for subsequent resolutions. A mode-only change SHALL NOT change embedding identity or require corpus re-embedding. Existing optimistic concurrency, secret retention, default-profile, and embedding compatibility protections SHALL remain applicable.

#### Scenario: Older client updates a native-enabled profile
- **WHEN** a valid update request omits `structuredOutputMode` or supplies null on a native-enabled profile
- **THEN** the saved mode remains `NATIVE_JSON_SCHEMA`
- **AND** existing omitted-secret retention behavior is preserved

#### Scenario: Mode-only update with existing embeddings
- **WHEN** an operator changes only the output mode on a profile assigned to knowledge bases with existing embeddings
- **THEN** the valid update succeeds and advances the profile revision using existing mutation semantics
- **AND** subsequent model resolutions observe the new mode
- **AND** no embeddings or graph indexes are rebuilt

#### Scenario: Explicit portable rollback
- **WHEN** an operator saves `PORTABLE` on a native-enabled profile
- **THEN** subsequently resolved eligible calls use portable output
- **AND** already captured operations retain their captured model and mode

#### Scenario: Incompatible embedding change accompanies a mode edit
- **WHEN** a mode edit also attempts an embedding change incompatible with stored embeddings
- **THEN** the existing compatibility check rejects the entire profile update
- **AND** the prior mode, revision, and assignments remain unchanged

#### Scenario: Stale profile mutation
- **WHEN** a mode-bearing update conflicts with the existing optimistic concurrency rules
- **THEN** the update is rejected as a conflict
- **AND** the persisted profile configuration remains unchanged
