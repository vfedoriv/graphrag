## Why

Recent PR review findings exposed several data-integrity and robustness gaps in schema generation and graph persistence. The most important risk is silent Neo4j identity collision when composite key values are converted into string IDs with ambiguous delimiters, followed by generated schemas and extraction payloads reaching later stages with incomplete or schema-invalid key/property data.

## What Changes

- Fix the schema-generation key prompt so it clearly states that every key component must be declared in the same node's properties.
- Harden generated schema key handling so generated schemas do not fabricate invalid `id` keys and do not silently preserve key candidates that are absent from declared properties.
- Preserve the existing non-blocking advisory behavior for generated schema responses while making key/property issues clearer and safer for downstream validation.
- Validate composite relationship endpoint keys component-by-component before graph persistence.
- Introduce collision-resistant stable graph IDs for nodes and relationships instead of delimiter-concatenated IDs.
- Filter extracted node and relationship property maps to schema-declared properties before writing them to Neo4j.
- Add regression tests for node key normalization, schema-generation key inference/advisories, transformer contract boundaries, composite endpoint validation, and graph identity generation.

## Capabilities

### New Capabilities
- `graph-identity-persistence`: Stable persisted identity for extracted graph nodes and relationships, including collision resistance for composite keys.

### Modified Capabilities
- `schema-generation-with-examples`: Clarify key/property prompt behavior and generated schema key inference/advisory behavior.
- `schema-definition-validation`: Keep schema validation as the final contract for saved schemas while aligning generated-schema output with that contract.
- `graph-extraction-result-contract`: Require relationship endpoint keys and persisted properties to remain schema-constrained after extraction normalization.

## Impact

- Affected code: `LangChain4jSchemaGenerationService`, `LLMGraphTransformerExt`, `GraphExtractionValidationService`, `GraphWriteService`, `NodeKeySupport`, and related unit/integration tests.
- API impact: no endpoint shape changes are expected. Existing schema-generation responses still return generated content plus advisory warnings.
- Data impact: newly written extracted graph nodes/relationships receive stable IDs from a safer canonical encoding or digest. Existing persisted extracted graph IDs are not migrated by this change.
- Dependency impact: no new external dependency is expected; Java standard hashing/encoding utilities are sufficient.
