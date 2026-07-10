## MODIFIED Requirements

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
