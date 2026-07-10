## MODIFIED Requirements

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
