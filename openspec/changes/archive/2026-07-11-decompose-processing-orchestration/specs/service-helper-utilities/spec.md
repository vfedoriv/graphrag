## ADDED Requirements

### Requirement: Workflow deterministic collaborators are extracted and pure
The system SHALL keep deterministic processing-option, chunk-metadata, schema-mapping, prompt, runtime-setting codec, and model-response mapping logic in cohesive collaborators that do not perform infrastructure side effects.

#### Scenario: Deterministic collaborator is unit tested
- **WHEN** a deterministic workflow collaborator receives explicit inputs
- **THEN** it returns a structured output without repository, Neo4j, filesystem, network, or model-client calls
- **AND** focused unit tests cover its supported and invalid inputs
