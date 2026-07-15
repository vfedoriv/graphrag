# multi-source-schema-discovery Specification

## Purpose
Define bounded, evidence-backed multi-source schema discovery that produces deterministic review-only results without persisting schemas or source content.
## Requirements
### Requirement: Knowledge-base schema discovery accepts multiple bounded sources
The system SHALL expose synchronous knowledge-base-scoped schema discovery operations that accept one or more independently identifiable sources, enforce configured request and per-source size limits before model invocation, and use the target knowledge base active AI profile.

#### Scenario: Discover from document and text sources
- **WHEN** a client submits valid document identifiers and pasted text samples to the JSON discovery operation for a managed knowledge base
- **THEN** the system analyzes each source independently and returns one combined discovery response
- **AND** every referenced document belongs to the target knowledge base
- **AND** every model call uses the active AI profile resolved for that knowledge base

#### Scenario: Discover from multiple files and metadata sources
- **WHEN** a client submits multiple supported files with request metadata that also contains document identifiers or pasted text samples to the multipart discovery operation
- **THEN** the system parses and analyzes every accepted source without concatenating source contents
- **AND** request-scoped files and pasted text are not persisted after the response completes

#### Scenario: Source limit is exceeded
- **WHEN** a discovery request exceeds the configured source count, individual source size, or total request size limit
- **THEN** the system rejects the request before starting any model call
- **AND** no partial discovery result or registered schema is created

#### Scenario: Referenced document is not owned by the knowledge base
- **WHEN** a discovery request references a missing document or a document owned by another knowledge base
- **THEN** the system rejects the request using the established problem detail conventions
- **AND** it does not expose whether a document exists in another knowledge base

### Requirement: Discovery guidance is structured and first-class
The system SHALL accept free-form additional instructions and typed generation guidance for domain description, intended questions, required and preferred nodes and relationships, excluded concepts, naming rules, identity guidance, expected property types, and cardinality guidance.

#### Scenario: Required concept has limited document evidence
- **WHEN** structured guidance marks a concept as required and the concept has limited or no supporting document evidence
- **THEN** the response includes a guided candidate for the concept
- **AND** the candidate is not reported as observed
- **AND** the candidate remains review-required

#### Scenario: Preferred concept is unsupported
- **WHEN** structured guidance marks a concept as preferred but no source supports it
- **THEN** the system does not present the concept as an observed candidate
- **AND** the response identifies that the preference lacked source evidence

#### Scenario: Excluded concept is extracted by a source
- **WHEN** a source analysis proposes a concept listed in the exclusion guidance
- **THEN** the aggregate excludes the concept from the review-only schema projection
- **AND** the response retains a suppression warning with source support metadata

#### Scenario: Guidance is invalid
- **WHEN** guidance contains duplicate contradictory rules, unsafe schema identifiers, unsupported property types, or relationships whose endpoints are missing
- **THEN** the system rejects the request before model invocation with field-specific validation errors

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

### Requirement: Candidate aggregation is deterministic and conflict-aware
The system SHALL normalize and aggregate valid candidates in application code with deterministic ordering and SHALL NOT allow a model response to silently select between incompatible candidate definitions.

#### Scenario: Compatible candidates repeat across sources
- **WHEN** multiple sources yield the same normalized candidate with compatible definitions
- **THEN** the system produces one aggregate candidate with unioned evidence and an independent-document support count
- **AND** repeated chunks from one document do not inflate the independent-document support count

#### Scenario: Property types conflict
- **WHEN** candidates propose incompatible types for the same normalized property
- **THEN** the aggregate retains the competing definitions and reports a review-required type conflict
- **AND** it does not silently use the first observed type

#### Scenario: Identity keys conflict
- **WHEN** candidates propose different identity keys for the same node label
- **THEN** the aggregate reports a review-required key conflict
- **AND** it does not silently replace or combine the competing keys

#### Scenario: Relationship semantics conflict
- **WHEN** candidates propose competing relationship names, endpoints, or directions
- **THEN** the aggregate reports each alternative and its evidence as a review-required conflict

#### Scenario: Model suggests aliases
- **WHEN** source analysis suggests that differently named concepts are aliases or canonical-name alternatives
- **THEN** application code reports an alias suggestion with its evidence
- **AND** the concepts remain distinct until a user explicitly resolves them in a persistent draft workflow

### Requirement: Discovery returns a review-only result with per-source outcomes
The system SHALL return a review-only schema projection together with aggregate candidates, conflicts, warnings, source outcomes, and reproducibility metadata, and SHALL NOT persist or activate the projected schema.

#### Scenario: All sources succeed
- **WHEN** every accepted source is analyzed successfully
- **THEN** the response status is `COMPLETED`
- **AND** the response includes candidates, conflicts, warnings, deterministic schema JSON, AI profile identifier and revision, and prompt contract revision

#### Scenario: Some sources fail
- **WHEN** at least one source succeeds and at least one source fails
- **THEN** the response status is `PARTIAL`
- **AND** successful sources contribute to the result
- **AND** failed sources are listed with retryable or non-retryable error classifications

#### Scenario: All sources fail
- **WHEN** no source produces a valid candidate result
- **THEN** the request fails without returning an apparently valid empty schema
- **AND** no schema or draft is persisted

#### Scenario: Existing generation endpoints are used
- **WHEN** a client calls an existing single-source schema or example generation endpoint
- **THEN** its established request, response, persistence, and AI-profile behavior remains compatible

### Requirement: Discovery preserves privacy-safe observability
The system SHALL observe the overall discovery workflow and individual source model calls using metadata-first operational logging and the existing controlled AI content-capture policy.

#### Scenario: Discovery is logged normally
- **WHEN** a multi-source discovery completes or fails
- **THEN** normal logs may include identifiers, source types, fingerprints, counts, timings, statuses, and exception classes
- **AND** normal logs do not include source text, prompts, model responses, candidate payloads, or generated schema content

#### Scenario: AI content capture is enabled
- **WHEN** controlled AI observation content capture is enabled
- **THEN** source prompts and model responses are captured only through the existing `AiObservationService` policy
