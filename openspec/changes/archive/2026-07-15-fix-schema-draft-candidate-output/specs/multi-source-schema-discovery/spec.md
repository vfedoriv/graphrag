## MODIFIED Requirements

### Requirement: Each source produces typed evidence-backed candidates
The system SHALL request each source's typed node, node-property, node-key, relationship, and relationship-property candidates through the provider-independent prompt-based structured-output contract, SHALL convert normal assistant content into the typed candidate container, and SHALL validate converted candidates before aggregation. The system SHALL NOT require provider-native JSON Schema support or interpret reasoning metadata as final candidate output.

#### Scenario: Observed candidate is returned
- **WHEN** a source analysis identifies a schema element supported by an analysis chunk
- **THEN** the candidate includes a stable request source identifier, source content fingerprint, analysis chunk identifier, model confidence when supplied, and `OBSERVED` evidence origin
- **AND** the evidence contains no document text preview

#### Scenario: Candidate has multiple evidence origins
- **WHEN** the same normalized candidate is inherited from caller context, requested by guidance, and observed in a source
- **THEN** the aggregate candidate reports all applicable origins rather than selecting only one
- **AND** origin remains separate from review or conflict state

#### Scenario: Provider-native structured output is unavailable
- **WHEN** the active chat model does not support provider-native structured output
- **THEN** the system requests a prompt-formatted candidate object in normal assistant content and converts it through the typed structured-output converter
- **AND** it validates the converted candidates before aggregation

#### Scenario: Reasoning metadata accompanies a response
- **WHEN** an OpenAI-compatible provider returns reasoning metadata in addition to or instead of normal assistant content
- **THEN** the system does not interpret the reasoning metadata as candidate output
- **AND** only valid normal assistant content may contribute candidates

#### Scenario: Model output violates the candidate contract
- **WHEN** normal assistant content is missing, blank, cannot be converted, or fails candidate validation
- **THEN** that source has a failed outcome with a privacy-safe error classification
- **AND** invalid candidates from that source do not enter the aggregate
