## ADDED Requirements

### Requirement: Model failure diagnostics are bounded and content-safe
The system SHALL make source-model failures operationally diagnosable using bounded structured metadata while treating raw exception and response content as sensitive.

#### Scenario: Retried model output is logged
- **WHEN** a candidate output attempt is retried because its normal assistant output is unusable
- **THEN** the warning identifies the draft/run/source/chunk context when applicable, logical output attempt, elapsed time, broad category, detailed code, response length/fingerprint, finish reason, and token counts when available
- **AND** it does not identify the event as an internal SDK HTTP attempt

#### Scenario: Terminal model failure is logged
- **WHEN** a source model call terminates unsuccessfully
- **THEN** the warning includes a bounded outer-to-root exception type chain, root exception type, provider status when available, configured timeout/retry metadata, and a non-reversible message fingerprint
- **AND** it excludes raw exception messages, headers, provider bodies, prompts, source text, normal or reasoning output, candidate payloads, and credentials

#### Scenario: Logging and trace capture use separate content policies
- **WHEN** AI observation content capture is enabled
- **THEN** normal application logs remain metadata-only
- **AND** any captured prompt or response content continues to flow only through `AiObservationService` according to its runtime privacy and length controls
