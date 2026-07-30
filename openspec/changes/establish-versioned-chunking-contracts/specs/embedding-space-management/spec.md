## ADDED Requirements

### Requirement: Tokenizer-aware embedding compatibility
The system SHALL include resolved tokenizer identity in the embedding-processing compatibility snapshot and SHALL reject a knowledge-base profile change that would make existing embedded chunks incompatible under the configured embedding-space policy.

#### Scenario: Compatible tokenizer metadata update
- **WHEN** a profile revision resolves to the same embedding model, dimensions, embedding-space identity, and tokenizer identity as existing chunks
- **THEN** the compatibility guard may accept the update

#### Scenario: Incompatible tokenizer change with chunks
- **WHEN** a profile update would change tokenizer identity for a knowledge base that already has embedded chunks
- **THEN** the update is rejected and the prior active profile remains unchanged
