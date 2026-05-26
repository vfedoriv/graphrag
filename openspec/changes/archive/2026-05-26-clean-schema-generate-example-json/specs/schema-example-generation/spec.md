## MODIFIED Requirements

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
