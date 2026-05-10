## ADDED Requirements

### Requirement: Preserve inferred graph metadata in generated schema YAML
The system SHALL preserve metadata inferred by the graph transformer when constructing generated schema YAML, including node descriptions, node properties, and edge descriptions.

#### Scenario: Text-based schema generation keeps node descriptions and properties
- **WHEN** `/api/v1/schemas/generate` produces inferred graph nodes with non-blank descriptions and properties
- **THEN** the generated YAML contains those node descriptions
- **AND** the generated YAML contains node properties with names and valid schema property types

#### Scenario: Text-based schema generation keeps edge descriptions
- **WHEN** `/api/v1/schemas/generate` produces inferred graph edges with non-blank descriptions
- **THEN** the generated YAML contains those edge descriptions for corresponding relationships

#### Scenario: File-based schema generation keeps inferred metadata
- **WHEN** `/api/v1/schemas/generate/from-file` produces inferred graph nodes and edges with descriptions and node properties
- **THEN** the generated YAML contains node descriptions, node properties, and edge descriptions for corresponding schema elements

#### Scenario: Missing optional metadata does not break generation
- **WHEN** inferred nodes or edges omit descriptions or properties
- **THEN** schema generation still succeeds
- **AND** only available metadata is included in the generated YAML
