## ADDED Requirements

### Requirement: Knowledge-base profile assignment is relational and compatibility-safe
The system SHALL update a knowledge base's AI profile in PostgreSQL only after validating embedding model and dimension compatibility with existing chunks.

#### Scenario: An incompatible profile is assigned
- **WHEN** chunks exist and the requested profile changes the effective embedding space incompatibly
- **THEN** the assignment is rejected
- **AND** the previous relational profile association remains unchanged

#### Scenario: A compatible profile is assigned
- **WHEN** the requested profile is compatible with the knowledge base's existing embedding space
- **THEN** the relational association commits atomically
