# schema-example-generation Specification

## Purpose
TBD - created by archiving change improve-schema-generation-examples. Update Purpose after archive.
## Requirements
### Requirement: Generate domain example from text
The system SHALL generate a domain example from unstructured text that contains representative entities and relationships suitable for use as schema generation guidance.

#### Scenario: Text example generation succeeds without prompt
- **WHEN** a client posts to `/api/v1/schemas/generate/example` with non-blank `text`
- **THEN** the system returns a generated example derived from the text
- **AND** the example includes representative entities and relationships

#### Scenario: Text example generation uses user prompt guidance
- **WHEN** a client posts to `/api/v1/schemas/generate/example` with non-blank `text` and `userPrompt`
- **THEN** the system includes the `userPrompt` as additional instruction to the LLM
- **AND** the returned example reflects relevant domain, entity, relationship, or property guidance when the source text supports it

#### Scenario: Text example generation rejects blank text
- **WHEN** a client posts to `/api/v1/schemas/generate/example` with missing or blank `text`
- **THEN** the system rejects the request as invalid
- **AND** no LLM example generation is invoked

### Requirement: Generate domain example from file
The system SHALL generate a domain example from an uploaded file by parsing the file content and using optional caller guidance.

#### Scenario: File example generation succeeds without prompt
- **WHEN** a client posts to `/api/v1/schemas/generate/example/from-file` with a supported `file`
- **THEN** the system parses the file and returns a generated example derived from the extracted text
- **AND** the example includes representative entities and relationships

#### Scenario: File example generation uses user prompt guidance
- **WHEN** a client posts to `/api/v1/schemas/generate/example/from-file` with a supported `file` and `userPrompt`
- **THEN** the system includes the `userPrompt` as additional instruction to the LLM
- **AND** the returned example reflects relevant domain, entity, relationship, or property guidance when the file content supports it

#### Scenario: File example generation rejects unreadable input
- **WHEN** a client posts to `/api/v1/schemas/generate/example/from-file` with a missing, empty, unreadable, or unsupported file
- **THEN** the system rejects the request as invalid
- **AND** no LLM example generation is invoked after parsing fails

### Requirement: Generated examples are reviewable text
The system SHALL return generated examples as normalized text that callers can inspect and edit before using them in schema generation.

#### Scenario: Example response is reviewable
- **WHEN** a client successfully generates an example from text or file input
- **THEN** the response contains generated example text
- **AND** the response does not generate or persist schema YAML

#### Scenario: Example response is normalized JSON text without leading blank-line artifacts
- **WHEN** a client successfully generates an example whose model output contains leading blank lines or leading newline escape artifacts before JSON content
- **THEN** the `example` response value is normalized to remove leading/trailing whitespace artifacts
- **AND** the returned `example` value starts directly with JSON content such as `[` for array examples

