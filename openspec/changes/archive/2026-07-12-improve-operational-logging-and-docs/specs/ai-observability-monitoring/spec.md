## ADDED Requirements

### Requirement: Application logs do not mirror AI observation content
The system SHALL keep application logging independent from AI observation input/output capture settings.

#### Scenario: AI content capture is enabled
- **WHEN** AI observability content capture is enabled for a model call
- **THEN** the centralized observation contains content according to its configured privacy and length limits
- **AND** normal application logs still contain only metadata and observation identifiers

#### Scenario: AI content capture is disabled
- **WHEN** AI observability content capture is disabled for a model call
- **THEN** the observation uses its configured sanitized representation
- **AND** application logs do not independently expose the input or output content
