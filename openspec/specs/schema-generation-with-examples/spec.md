# schema-generation-with-examples Specification

## Purpose
TBD - created by archiving change improve-schema-generation-examples. Update Purpose after archive.
## Requirements
### Requirement: Generate schema YAML from text with a domain example
The system SHALL generate graph schema JSON from unstructured text only when the request includes schema metadata, source text, and a non-blank domain example containing representative entities and relationships for the source domain.

#### Scenario: Text schema generation succeeds with example
- **WHEN** a client posts to `/api/v1/schemas/generate` with `name`, `version`, `description`, `text`, and `example`
- **THEN** the system returns generated JSON for the inferred graph schema
- **AND** the graph extraction step uses the provided `example` as domain guidance

#### Scenario: Text schema generation rejects missing example
- **WHEN** a client posts to `/api/v1/schemas/generate` without `example` or with a blank `example`
- **THEN** the system rejects the request as invalid
- **AND** no LLM schema generation is invoked

### Requirement: Generate schema YAML from file with a domain example
The system SHALL generate graph schema JSON from an uploaded file only when the multipart request includes schema metadata, a supported file, and a non-blank domain example containing representative entities and relationships for the file domain.

#### Scenario: File schema generation succeeds with example
- **WHEN** a client posts to `/api/v1/schemas/generate/from-file` with `name`, `version`, optional `description`, `example`, and `file`
- **THEN** the system parses the file, generates JSON for the inferred graph schema, and returns the JSON
- **AND** the graph extraction step uses the provided `example` as domain guidance

#### Scenario: File schema generation rejects missing example
- **WHEN** a client posts to `/api/v1/schemas/generate/from-file` without `example` or with a blank `example`
- **THEN** the system rejects the request as invalid
- **AND** no LLM schema generation is invoked

### Requirement: Generated schema endpoints do not persist schemas
The system SHALL NOT save generated schema JSON from schema generation endpoints directly to the schema registry.

#### Scenario: Generated schema is returned for review only
- **WHEN** a client successfully generates schema JSON from text or file input
- **THEN** the response contains the generated JSON
- **AND** no generated schema record is created in the schema registry
- **AND** the response does not identify a saved schema version

#### Scenario: Save parameter is not accepted
- **WHEN** a client sends a `save` field or `save` multipart parameter to a schema generation endpoint
- **THEN** the system does not use it to persist the generated JSON
- **AND** clients must use the schema creation endpoint to save reviewed JSON

### Requirement: Preserve inferred graph metadata in generated schema YAML
The system SHALL preserve metadata inferred by the graph transformer when constructing generated schema JSON, including node descriptions, node properties, and edge descriptions.

#### Scenario: Text-based schema generation keeps node descriptions and properties
- **WHEN** `/api/v1/schemas/generate` produces inferred graph nodes with non-blank descriptions and properties
- **THEN** the generated JSON contains those node descriptions
- **AND** the generated JSON contains node properties with names and valid schema property types

#### Scenario: Text-based schema generation keeps edge descriptions
- **WHEN** `/api/v1/schemas/generate` produces inferred graph edges with non-blank descriptions
- **THEN** the generated JSON contains those edge descriptions for corresponding relationships

#### Scenario: File-based schema generation keeps inferred metadata
- **WHEN** `/api/v1/schemas/generate/from-file` produces inferred graph nodes and edges with descriptions and node properties
- **THEN** the generated JSON contains node descriptions, node properties, and edge descriptions for corresponding schema elements

#### Scenario: Missing optional metadata does not break generation
- **WHEN** inferred nodes or edges omit descriptions or properties
- **THEN** schema generation still succeeds
- **AND** only available metadata is included in the generated JSON

### Requirement: Transformer extension preserves inferred node and edge properties
The system SHALL use a transformer implementation for schema generation that preserves node and edge `properties` extracted from model output when such properties are present.

#### Scenario: Node properties are preserved from transformer output
- **WHEN** schema generation processes model output containing node property maps
- **THEN** the graph document used by schema inference contains non-empty `GraphNode.properties` entries for corresponding nodes

#### Scenario: Edge properties are preserved from transformer output
- **WHEN** schema generation processes model output containing edge property maps
- **THEN** the graph document used by schema inference contains non-empty `GraphEdge.properties` entries for corresponding relationships

#### Scenario: Missing properties do not break transformation
- **WHEN** model output omits node or edge properties
- **THEN** transformation succeeds without error
- **AND** the resulting node or edge properties remain empty rather than causing invalid graph output

### Requirement: Transformer extension behavior is regression tested
The system SHALL include automated tests for transformer extension behavior to ensure node and edge property extraction remains stable.

#### Scenario: Unit tests validate node and edge property extraction
- **WHEN** transformer extension tests run with representative model output fixtures
- **THEN** tests verify that node and edge properties are parsed and populated as expected
- **AND** tests fail if properties are dropped unexpectedly

### Requirement: Prompt contract enforces key/property consistency
The system SHALL instruct schema generation models that each node definition `key` must match one property name declared in that same node's `properties` list.

#### Scenario: Model receives schema generation prompt
- **WHEN** the system constructs the schema generation prompt
- **THEN** the prompt includes an explicit constraint that node `key` values must be present in node `properties[].name`
- **AND** the prompt discourages generic `id` unless `id` is explicitly declared as a property

### Requirement: Post-generation key/property mismatch advisory
The system SHALL analyze generated schemas and return advisory warnings/suggestions when a node key is not declared in that node properties.

#### Scenario: Generated node key is missing from properties
- **WHEN** a generated schema contains a node where `key` is not present in `properties[].name`
- **THEN** the generation response includes a warning describing the mismatch
- **AND** the generation response includes at least one suggestion to resolve it

#### Scenario: Multiple generated nodes have key/property mismatches
- **WHEN** a generated schema contains key/property mismatches on multiple nodes
- **THEN** the generation response includes warnings for all mismatched nodes
- **AND** warning order follows node order from the generated schema

### Requirement: Advisory checks do not block generation response
The system SHALL return generated schema content even when key/property advisory warnings are present.

#### Scenario: Generated schema has advisory warnings
- **WHEN** advisory key/property checks detect mismatches
- **THEN** schema generation still returns the generated schema payload
- **AND** warnings are attached without converting the response into a hard validation failure

