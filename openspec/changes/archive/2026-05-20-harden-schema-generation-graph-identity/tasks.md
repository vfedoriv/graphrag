## 1. Schema Generation Key Hygiene

- [x] 1.1 Fix the truncated `SCHEMA_PROMPT_CONTRACT` sentence and update prompt assertions to cover the complete key/property constraint.
- [x] 1.2 Update key candidate normalization to drop blank, duplicate, unsafe, and undeclared key candidates before building generated node definitions.
- [x] 1.3 Change empty-property key inference so schema generation does not fabricate an undeclared `id` key.
- [x] 1.4 Extend advisory warnings to report missing or discarded key components with actionable suggestions.
- [x] 1.5 Add tests for inferred key fallback branches, composite key mismatch warnings, and invalid model-provided key candidates.

## 2. Transformer Contract Coverage

- [x] 2.1 Add tests for missing `key`, key referencing undeclared properties, partial composite keys, and useful-property count boundaries in `LLMGraphTransformerExt`.
- [x] 2.2 Review transformer exception messages and remove unnecessary raw model-controlled detail from user-facing errors if needed.
- [x] 2.3 Ensure array-valued property rejection remains covered without failing during response-summary logging in a misleading way.

## 3. Composite Key Validation

- [x] 3.1 Add `NodeKeySupport` unit tests for null input, blank filtering, deduplication, order preservation, and display formatting.
- [x] 3.2 Validate every required node key component after node normalization.
- [x] 3.3 Validate every required relationship endpoint key component after endpoint-key fill attempts.
- [x] 3.4 Add validation tests for complete composite endpoints, partial composite endpoints, and fill behavior for unambiguous single-node endpoints.

## 4. Graph Identity Persistence

- [x] 4.1 Replace delimiter-concatenated node stable IDs with deterministic IDs derived from canonical identity material and SHA-256.
- [x] 4.2 Replace delimiter-concatenated relationship stable IDs with deterministic IDs derived from canonical relationship identity material and SHA-256.
- [x] 4.3 Add focused tests proving delimiter-containing key values do not collide and repeated writes derive identical IDs.
- [x] 4.4 Preserve useful debug/provenance properties such as schema id, label/type context, source document id, source chunk ids, extraction run id, confidence, and created timestamp.

## 5. Schema-Constrained Property Writes

- [x] 5.1 Filter extracted node properties to properties declared for the node label before `SET n += $props`, while retaining system metadata.
- [x] 5.2 Filter extracted relationship properties to properties declared for the matching relationship definition before `SET r += $props`, while retaining system metadata.
- [x] 5.3 Log dropped undeclared property names with sanitized context.
- [x] 5.4 Add graph write tests for undeclared node and relationship properties being omitted from persisted maps.

## 6. Verification

- [x] 6.1 Run targeted unit tests for schema generation, graph extraction validation, graph writes, and node key support.
- [x] 6.2 Run `./mvnw test`.
- [x] 6.3 Run `openspec status --change "harden-schema-generation-graph-identity"` and confirm the change is apply-ready.
