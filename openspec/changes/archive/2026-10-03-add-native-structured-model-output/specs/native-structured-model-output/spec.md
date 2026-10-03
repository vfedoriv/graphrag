## Purpose

Define opt-in native JSON Schema output for graph extraction and Cypher generation while preserving portable provider interoperability, existing domain results, semantic guardrails, and privacy-safe failure diagnostics.

## ADDED Requirements

### Requirement: Output enforcement is selected explicitly for each eligible model call
The system SHALL use the resolved AI profile's structured-output mode for graph extraction and Cypher generation. `PORTABLE` SHALL retain prompt-based output without a provider-native response schema. `NATIVE_JSON_SCHEMA` SHALL send a strict closed JSON Schema for the requested workflow. The system MUST NOT infer support from a client class, endpoint URL, or model name and MUST NOT automatically probe or downgrade the selected mode.

#### Scenario: Portable graph extraction
- **WHEN** graph extraction resolves a profile configured as `PORTABLE`
- **THEN** the provider request uses the existing portable prompt contract
- **AND** no native response schema is attached

#### Scenario: Native Cypher generation
- **WHEN** Cypher generation resolves a profile configured as `NATIVE_JSON_SCHEMA`
- **THEN** the provider request contains the Cypher output JSON Schema with strict enforcement enabled
- **AND** the configured model, timeout, and retry policy remain in effect

#### Scenario: Native graph extraction
- **WHEN** graph extraction resolves a profile configured as `NATIVE_JSON_SCHEMA`
- **THEN** the provider request contains the graph output JSON Schema with strict enforcement enabled
- **AND** the prompt retains the active schema, complete allowed relationship triples, and rule to omit relationships outside those triples

#### Scenario: Unscoped custom model has no mode declaration
- **WHEN** an eligible workflow uses a fallback model with no explicit mode-bearing profile binding
- **THEN** it retains portable behavior
- **AND** the model's implementation class does not enable native output

### Requirement: Native output configuration does not affect unrelated workflows
The system SHALL apply native enforcement only to graph extraction and Cypher generation. Schema discovery and draft candidate analysis, schema generation, and other model workflows SHALL retain their existing portable request contracts even when they use the same AI profile as a native-enabled eligible workflow.

#### Scenario: Discovery shares a native-enabled profile
- **WHEN** schema discovery runs using a profile configured as `NATIVE_JSON_SCHEMA`
- **THEN** candidate extraction still uses the established portable prompt-based contract
- **AND** it reads only normal assistant content for candidates

#### Scenario: Advanced search uses multiple model stages
- **WHEN** advanced search uses a native-enabled profile
- **THEN** planning, reranking, sufficiency evaluation, and answer synthesis retain their existing portable output requests
- **AND** any reused eligible Cypher generation call uses its selected mode

### Requirement: Captured calls retain one consistent model and output mode
The system SHALL resolve a call's model and output mode from the same profile revision. Captured execution SHALL retain that resolved model and mode despite later profile edits and SHALL restore the prior binding after nested execution or failure.

#### Scenario: Profile changes after capture
- **WHEN** a model and `PORTABLE` mode have been captured for an operation
- **AND** the profile is subsequently changed to `NATIVE_JSON_SCHEMA`
- **THEN** the captured operation continues using its original model and `PORTABLE` mode
- **AND** a subsequent capture resolves the updated revision and mode

#### Scenario: Nested captured operation fails
- **WHEN** an inner captured operation with a different profile/mode raises an exception
- **THEN** the outer operation resumes with its original model/mode binding
- **AND** no inner binding remains after the outer scope exits

### Requirement: Native conversion preserves existing graph and query result contracts
The system SHALL convert accepted native output into the existing graph extraction collections and property/key maps or generated Cypher string, explanation, and parameter map. It SHALL preserve scalar value types, integer precision, null values, nested maps, list order, and empty collections without exposing provider-specific envelopes in public responses. Ambiguous duplicate map names, duplicate envelope fields, or invalid value structures SHALL fail conversion instead of silently overwriting or coercing values.

#### Scenario: Native output contains typed dynamic values
- **WHEN** native graph or Cypher output contains strings, booleans, integers, fractional numbers, explicit nulls, lists, and nested objects under dynamically named entries
- **THEN** downstream consumers receive corresponding ordinary map/list/scalar values
- **AND** values are not converted to JSON strings or all coerced to floating-point numbers

#### Scenario: Large integer parameter
- **WHEN** a native parameter contains an integer greater than the largest exactly representable integer in a floating-point double
- **THEN** conversion preserves its exact integer value
- **AND** any existing execution-layer type restrictions remain applicable

#### Scenario: Empty extraction or parameter set
- **WHEN** native output supplies empty node/relationship collections or an empty parameter set
- **THEN** conversion returns the corresponding empty collections/maps
- **AND** it does not invent graph elements or parameter values

#### Scenario: Duplicate dynamic entry name
- **WHEN** a native payload supplies two entries with the same key in one map
- **THEN** conversion fails explicitly
- **AND** no output from that payload reaches graph persistence or query execution

#### Scenario: Extra graph element envelope field
- **WHEN** an otherwise usable native graph payload includes an unknown node or relationship envelope field
- **THEN** the unknown field is ignored with the existing warning behavior
- **AND** remaining known content still proceeds to semantic validation

### Requirement: Native failures do not produce successful empty or partial results
The system SHALL require completed normal assistant content before accepting native output. It SHALL fail explicitly for model refusal, incomplete completion, empty normal content, invalid required output fields, invalid entry conversion, or native format rejection/unavailability. It MUST NOT use reasoning metadata as the final output, silently retry using portable mode, or introduce additional output-repair attempts.

#### Scenario: Model refuses with parseable accompanying text
- **WHEN** a native model response reports refusal
- **THEN** the call fails as a refusal regardless of whether accompanying text appears parseable
- **AND** no extracted graph or generated query from that response proceeds to effects

#### Scenario: Token limit yields valid-looking JSON
- **WHEN** a native response reports token-limit truncation or another incomplete completion status
- **THEN** the call fails as incomplete output even if its text is syntactically valid JSON

#### Scenario: JSON appears only in reasoning
- **WHEN** a native response contains JSON in reasoning metadata but empty normal assistant content
- **THEN** the call fails as empty output
- **AND** the system does not convert reasoning into a graph or query result

#### Scenario: Provider rejects native response format
- **WHEN** the configured provider/model explicitly rejects the requested native format
- **THEN** the call fails with native-format rejection diagnostics
- **AND** no portable request is made as fallback
- **AND** the saved profile mode remains unchanged

#### Scenario: Native client cannot encode a schema request
- **WHEN** an explicitly native resolved model binding cannot send the required JSON Schema
- **THEN** the call fails with native-format-unavailable diagnostics before a downgraded provider request is sent

#### Scenario: Ordinary provider failure
- **WHEN** a native call encounters authentication, rate-limit, timeout, or generic provider failure
- **THEN** existing provider-failure behavior and configured SDK retry policy are retained
- **AND** the failure is not automatically reported as unsupported native formatting

### Requirement: Semantic validation and effect guardrails remain authoritative
The system SHALL keep existing graph normalization, invalid-element filtering, endpoint identity repair, allowed-property persistence, and fatal extraction limits after native decoding. Generated native Cypher SHALL pass the same read-only, schema-reference, limit, and planner validation before execution as portable output. Native shape conformance SHALL NOT authorize effects on its own.

#### Scenario: Native graph has an invalid relationship triple
- **WHEN** native output decodes successfully but includes a relationship outside the active schema's allowed triples
- **THEN** semantic validation omits that relationship
- **AND** continues processing remaining valid elements

#### Scenario: Native graph exceeds extraction limits
- **WHEN** decoded native output exceeds the configured entity or relationship maximum
- **THEN** validation fails the extraction
- **AND** no graph data from that output is persisted

#### Scenario: Native query contains a mutating statement
- **WHEN** native output decodes successfully but its Cypher violates read-only policy
- **THEN** validation rejects the query
- **AND** query execution is not called

#### Scenario: Native query fails planner validation
- **WHEN** native generated Cypher fails the existing planner check
- **THEN** the existing query rejection behavior applies
- **AND** native output does not bypass that check

### Requirement: Output mode and failures are observable without content leakage
The system SHALL record the effective output mode, workflow output-contract version, and safe outcome category for eligible calls using existing observations. Normal logs SHALL remain metadata-first and SHALL NOT expose source text, user prompts, generated schemas, graph payloads, Cypher parameter values, model/refusal text, reasoning content, or raw provider messages. Controlled trace content capture SHALL retain existing settings and existing HTTP error envelopes SHALL remain compatible.

#### Scenario: Native failure diagnostics
- **WHEN** a native call fails during provider response handling or conversion
- **THEN** observations identify the effective mode, output contract, and safe failure category
- **AND** ordinary logs contain only safe metadata and fingerprints

#### Scenario: Portable regression
- **WHEN** an existing profile remains portable
- **THEN** existing graph tolerance, Cypher parsing, validation, error envelopes, and privacy behavior remain available
- **AND** diagnostics identify the call as portable without exposing content
