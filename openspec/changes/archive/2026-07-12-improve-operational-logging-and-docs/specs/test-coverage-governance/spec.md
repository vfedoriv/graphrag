## ADDED Requirements

### Requirement: Operational privacy and documentation alignment have regression coverage
The project SHALL test content-safe application logging, observability/log separation, test logging configuration, and build-backed contributor documentation facts.

#### Scenario: Sensitive content is supplied to a workflow
- **WHEN** a deterministic test supplies recognizable document, prompt, query, or model-response content
- **THEN** normal captured application logs do not contain that content
- **AND** required operational metadata remains available

#### Scenario: Documentation fact is changed
- **WHEN** a documented stack or shared configuration fact diverges from the canonical build configuration
- **THEN** the regression check fails with the inconsistent documentation location
