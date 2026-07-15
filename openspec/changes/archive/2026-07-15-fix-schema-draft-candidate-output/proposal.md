## Why

Schema-draft candidate analysis currently enables provider-native JSON Schema for every Spring AI `OpenAiChatModel`. With the configured LM Studio reasoning model, a native structured-output request returns schema-conforming JSON in `reasoning_content` while leaving normal assistant content empty, so the backend converts an empty string and persists `CONVERSION_ERROR` even though equivalent prompt-based JSON calls succeed.

## What Changes

- Standardize schema-draft candidate extraction on Spring AI's portable prompt-based structured-output contract instead of attaching provider-native `OpenAiChatOptions`.
- Continue converting the normal assistant content through `BeanOutputConverter` and validating candidates before aggregation; do not interpret model reasoning metadata as final output.
- Remove model-class-based native-output detection and the unused native prompt/options path.
- Preserve the number of prepared analysis chunks when a source fails after preparation.
- Add privacy-safe per-source failure diagnostics using exception classification and a non-reversible message fingerprint, without logging prompts, source content, model output, reasoning, or candidate payloads.
- Add regression coverage for portable prompt construction, normal-content conversion, empty or malformed responses, failure chunk counts, and safe diagnostics.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multi-source-schema-discovery`: Require candidate extraction to use the provider-independent prompt-based structured-output contract and normal assistant content rather than provider-native JSON Schema or reasoning metadata.
- `schema-draft-analysis`: Require failed source outcomes to retain prepared chunk counts and emit privacy-safe classified diagnostics.

## Impact

- Candidate extraction and prompt construction in `CandidateExtractionModelAdapter` and `DiscoverySourceAnalyzer`.
- Per-source failure handling in `SchemaDraftAnalysisService`.
- Adapter and analysis service tests, plus relevant integration coverage.
- No HTTP API shape, persisted schema, AI-profile contract, dependency, or frontend change is required.
- Portable prompt-based output may use more model tokens and take longer than provider-native constrained decoding, but it matches the existing interoperable request pattern used by other backend LLM calls.
