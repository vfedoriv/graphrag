## Why

Graph extraction and Cypher generation currently request JSON through prompt instructions and then parse unconstrained assistant text. Explicitly opting compatible provider/model profiles into native JSON Schema output can reduce formatting failures without sacrificing the portable path needed by other OpenAI-compatible providers.

## What Changes

- Add a non-secret, revisioned AI-profile `structuredOutputMode` with `PORTABLE` as the compatibility default and explicit opt-in `NATIVE_JSON_SCHEMA`.
- Apply native output only to document graph extraction and Cypher generation. Keep schema discovery/draft candidate analysis, schema generation, and other model workflows portable.
- Supply strict, closed JSON Schemas using adapter-internal typed entries for dynamically named property, endpoint-key, and parameter maps; convert accepted output back to existing domain and HTTP map contracts without losing value types.
- Preserve graph normalization, element filtering, extraction limits, Cypher safety checks, `EXPLAIN`, and execution policy. Native format conformance does not establish semantic correctness.
- Reject refusals, incomplete output, malformed native payloads, and unsupported native requests explicitly. Do not silently downgrade to portable output or treat reasoning metadata as the final response.
- Record privacy-safe mode/contract/failure diagnostics and add deterministic request-contract, conversion, compatibility, and guardrail regression coverage.

## Capabilities

### New Capabilities

- `native-structured-model-output`: Mode-aware graph extraction and Cypher generation, strict internal output contracts, lossless conversion to existing public contracts, explicit failures, and safe diagnostics.

### Modified Capabilities

- `ai-profile-management`: Persist, validate, expose, and revision the optional structured-output mode while retaining portable defaults and existing profile/embedding guarantees.

## Impact

- Profile API DTOs, non-secret profile facts, PostgreSQL entity/repository mappings, Flyway migration, and profile update/cache behavior under `ai`.
- AI-owned model resolution and captured execution must carry the selected mode with the corresponding model/profile revision; feature model adapters receive only public model capabilities and immutable facts.
- `documents.adapters.model.SpringAiGraphExtractionClient` and `search.query.adapters.model.SpringAiCypherGenerationClient`, including internal schema/response codecs and their tests.
- Existing public extraction/query response shapes, embeddings, graph indexes, stored artifacts, and relational/external-work separation remain unchanged. The profile API gains an optional request field and non-secret response field.
- The existing Spring AI 2.1.0-M1 OpenAI integration supports request-level JSON Schema; no Responses API migration or new provider SDK is required.
- Matching portal workflow/profile pages and overlapping contributor guidance must describe opt-in support and failure behavior in the implementation change.

## Non-Goals

- Responses API migration, hosted tools/conversations, automatic provider probing, model-class or URL-based capability inference, automatic fallback, new output-repair retries, or changing schema discovery's established portable contract.
- Claiming improved extraction recall, query correctness, cost, or latency without provider-specific measurements.
