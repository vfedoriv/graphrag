## Context

See `proposal.md` for motivation and scope. Current graph extraction and Cypher generation call a Spring AI `ChatModel` with a plain `Prompt`, then parse normal assistant content. Graph parsing tolerates unknown envelope fields and extracts fenced JSON; Cypher parsing directly reads a JSON object. Both return dynamic `Map<String, Object>` values. Graph validation repairs incomplete identities, filters invalid elements, and enforces per-chunk limits; query execution separately validates read-only syntax, schema references, limits, and `EXPLAIN`.

Profiles are PostgreSQL-backed and revisioned. `AiRuntimeModelFactory` constructs and caches models by profile ID/revision. `DefaultAiExecution` currently captures only a `ChatModel`; `DefaultProfileScopedAiClientResolver` prefers that captured model over current profile resolution. The two target adapters currently resolve models themselves and must use the public resolver/capability path when adopting mode-aware calls.

The archived `2026-07-15-fix-schema-draft-candidate-output` change documents a confirmed LM Studio failure: native schema output appeared in reasoning metadata with empty normal content. Consequently, `multi-source-schema-discovery` explicitly requires portable prompting. This proposal preserves that decision.

The installed Spring AI 2.1.0-M1 sources support `OpenAiChatOptions.responseFormat` with `OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA` and a response-format-specific `strict` flag. The unrelated chat-options `strict` flag controls tool calls. OpenAI's strict schema subset requires a root object, closed object definitions, and all declared fields required; nullable fields express optional values.

## Goals / Non-Goals

**Goals:**

- Make native format enforcement a deliberate provider/model configuration choice, with the chosen mode bound to the actual model revision used for a call.
- Keep domain contracts, public query JSON, extraction semantics, and existing portable behavior stable.
- Make failures distinguishable without exposing document text, query values, model output, or reasoning.
- Use fixed, testable schemas so behavior does not depend on the size or naming of a knowledge base's active schema.

**Non-Goals:**

- See the proposal's non-goals. In particular, this design does not establish a universal provider capability detector or expand native output into candidate discovery or advanced-search planning/reranking/synthesis.
- Native output does not verify factual grounding or prove that a Cypher string is read-only. It does not add inferred values for absent evidence.
- No new enclosing transaction around provider calls or changes to checkpoint/recovery predicates.

## Decisions

### 1. Persist explicit mode with backward-compatible omission semantics

Add an AI-owned enum `StructuredOutputMode` with `PORTABLE` and `NATIVE_JSON_SCHEMA`. Add `structuredOutputMode` to profile create/update/read contracts and non-secret `ProfileFacts`/`ProfileView` mappings, including KB profile reads that expose the same configuration.

- Create, default-profile seeding, and pre-existing rows default to `PORTABLE`.
- An omitted/null mode on update retains the saved mode, so older clients do not accidentally disable a new operator choice. Explicit `PORTABLE` is the supported rollback switch.
- Invalid strings return the existing validation problem envelope without changing profile state or revision.
- A saved mode change follows existing profile revision and cache invalidation semantics; it does not alter embedding identity, tokenizer compatibility, or assignments.
- Persist a non-null mode using a new forward Flyway migration with a portable default and allowed-value constraint. Do not edit historical migrations or rewrite existing run snapshots.

Opt-in means the operator asserts that the configured endpoint/model supports strict JSON Schema correctly. Saving a profile validates the enum and ordinary profile fields, without a provider call. Operational support is checked when the selected workflow makes a native request. Unknown custom clients or unscoped fallback beans remain portable; an explicitly native binding that cannot encode the request fails instead of downgrading.

Alternative: infer support from the client class, hostname, or model name. Rejected because the previous LM Studio failure used the same OpenAI-compatible client class. Alternative: automatic probing/fallback. Rejected because it adds requests, hidden behavior changes, and capability-cache invalidation without evidence that this complexity is needed.

### 2. Resolve model and mode as one immutable binding

Expose an AI-owned resolved chat binding through `ai.models`, containing the model handle, mode, and non-secret profile ID/revision. Construct it from one loaded profile revision and use the existing revision-scoped cache. Keep the existing plain `chatModel()` capabilities for unaffected consumers.

Extend the AI-owned captured execution context to retain this binding, not a model followed by a later profile-mode lookup. Preserve nesting, restoration after exceptions, and ordinary fallback behavior. Native-aware feature adapters resolve through the public profile-scoped model capability and do not access AI profile entities, repositories, or private provider context.

An already captured binding stays unchanged after a profile edit. A later capture resolves the new revision. Operations that currently resolve the active profile at call time continue to do so. This change does not strengthen existing durable-work snapshot guarantees or rebuild a historical revision from current profile data.

Do not set a global response format on cached models. A profile's native preference is applied per request only by the two selected adapters, so portable discovery and other consumers of the same cached model retain plain prompting.

Alternative: independently read current profile facts from each adapter. Rejected because a profile edit could pair a captured old model with a new mode. Alternative: replace all models with a wrapper that always forces native output. Rejected because output schemas differ by workflow and this would break the approved scope.

### 3. Use fixed internal envelopes with recursive typed map entries

Keep `GraphExtractionResult`, `GeneratedCypher`, and HTTP response map shapes unchanged. Only the provider-facing native payload represents a map as an ordered entry array:

```json
{"properties":[{"key":"contractId","value":"C-1"}]}
```

The entry array converts to `{"contractId":"C-1"}` at the adapter boundary. Empty entries produce an empty map. Graph `properties`, `fromKey`, and `toKey`, and Cypher `parameters`, all use this encoding. Graph `nodes`/`relationships` remain arrays; Cypher `cypher`/`explanation` remain strings. Nullable confidence values remain nullable rather than becoming an invented score.

Define a recursive native JSON value with supported JSON Schema primitives:

- Scalar branches: null, string, boolean, integer, and number.
- Array branch: an array of native JSON values, preserving order.
- Object branch: a closed wrapper `{"entries":[{"key":"...","value":...}]}` whose entries recursively reconstruct an ordinary nested map.

The object wrapper unambiguously distinguishes a nested object from a JSON array. All object definitions declare `additionalProperties:false` and list every property in `required`. Use `$defs`/`$ref` and nested `anyOf`; neither root envelope is an `anyOf`. Emit a versioned graph envelope and Cypher envelope with descriptive stable contract identifiers. Feature-owned codecs generate/own their output schemas and conversions; do not add document/query types to AI.

Keep value definitions and decoding private to the two feature adapter packages, with equivalent conversion-contract fixtures. Do not create a new shared support area or dependencies between documents and search for this change. The small amount of duplicated value representation is preferable to introducing an unproven shared ownership boundary.

Decode numeric tokens without turning integers into floating-point values or strings. Preserve explicit null versus a missing entry, empty maps, nested maps, arrays, booleans, and strings. Reject duplicate entry keys within one map, duplicate envelope fields, invalid entry structures, trailing non-JSON content, or unsupported JSON values before returning a domain result; do not silently overwrite or coerce. Apply standard bounded JSON parser constraints and existing payload limits rather than adding ad hoc untyped runtime settings.

Strict native responses are parsed as the declared envelope, without fenced-text extraction. Preserve graph extraction's existing tolerance for extra node/relationship envelope fields, with warning logs, even if a nonconforming native provider supplies them; validate required field types and entry structures instead of dropping the entire otherwise usable graph result for an extra field. Portable responses continue through their existing tolerant graph parser and existing Cypher parser. Do not remove unknown-field tolerance or repair behavior.

Keep labels, relationship types, and map keys as strings constrained semantically by the existing active-schema validation. Keep complete allowed relationship triples and omission instructions in both graph prompts. Native prompts explain their entry encoding and empty-result behavior, while portable prompt contracts remain unchanged.

Alternative: generate a separate `anyOf` branch for each active graph label/triple, with explicitly named properties. Rejected for this first change because it creates schema-size limits, schema compilation variation, nullable-field interpretation, and coupling to graph property types; it also cannot solve arbitrary Cypher parameter names. Alternative: encode maps as JSON strings. Rejected because this moves the same formatting problem inside an unconstrained string. Alternative: automatically derive schemas from existing DTOs. Rejected because unconstrained maps cannot satisfy strict closed-object requirements.

### 4. Attach strict response formats only to native requests

For a native binding, the two feature model adapters supply request-level `OpenAiChatOptions` with the appropriate JSON Schema and `ResponseFormat.strict(true)`. Retain model/base URL/key/timeouts/retries from the captured model defaults; do not rebuild clients or override credentials per request. A raw outbound-request test against a local HTTP stub verifies the actual `response_format.type=json_schema`, strict flag, schema, and absence of accidental option overrides.

For a portable binding, use the existing prompt and omit native response-format options. No dependency upgrade or Responses integration is necessary.

Alternative: migrate to `ChatClient.entity(...useProviderStructuredOutput())` immediately. Rejected because these adapters already own observations and conversion, generic framework fallback can hide unsupported native mode, and generated map schemas remain the underlying problem.

### 5. Accept only completed normal assistant content

Before decoding native output, require a usable normal assistant message and inspect the model's refusal/finish metadata. The installed `OpenAiChatModel` places `refusal` in assistant metadata and completion status in generation metadata. Test this mapping at the real SDK-to-Spring boundary, not only on handmade `ChatResponse` fixtures.

- Refusal: fail with a metadata-only `MODEL_REFUSAL` category, even if accompanying text looks parseable.
- Non-success completion, including token-limit truncation or content filtering: fail with `INCOMPLETE_MODEL_OUTPUT`; valid-looking partial JSON is not sufficient.
- Empty normal content: fail with `EMPTY_MODEL_OUTPUT`, including when reasoning metadata contains JSON.
- Invalid required native envelope fields or entry conversion: fail with `INVALID_NATIVE_OUTPUT`; graph unknown-field tolerance is retained as described above.
- Explicit provider rejection of the native format or unsupported schema: fail with `NATIVE_FORMAT_REJECTED`. A local client unable to encode native requests fails with `NATIVE_FORMAT_UNAVAILABLE`.
- Authentication, throttling, timeout, and transport failures retain their existing provider classifications. Do not label every HTTP 400 as a native-capability error; classify from safe structured error codes where available, otherwise retain generic provider failure.

Do not switch mode or add output-repair retries. Existing configured SDK retries retain their configured behavior. Preserve existing HTTP error envelopes and workflow run-failure behavior; categories are diagnostics rather than a new public response DTO. Failure must prevent graph persistence or query execution for that rejected output.

Successful native extraction still enters `GraphExtractionValidationService`. All query generation remains subject to the existing application validation before execution, including through `/ask` and callers reusing the Cypher client. Native schema validation never replaces these boundaries.

### 6. Keep observability metadata-first and verify the benefit separately

Add low-cardinality output mode, workflow contract version, and outcome category to existing model-call observations. Include safe counts, timing, finish reason, token usage, and fingerprints where already supported. Do not attach the generated schema, native payload, parameter values, refusal text, reasoning content, or raw provider exception message to ordinary logs. Preserve opt-in AI trace content capture through `AiObservationService`.

Add deterministic tests for request shape, full value conversion, captured-mode consistency, profile omission behavior, unsupported/refusal/incomplete outputs, portable regressions, and semantic guardrails. Reuse existing architecture and logging tests.

No benchmark claim is a release criterion. Document a manual opt-in evaluation recipe using the same provider/model, source corpus/prompts, timeout, and retry settings for each mode; compare parse failures, semantic acceptance/drop counts, latency, and token use. A real provider run is optional and requires configured access; the credential-free suite uses stubs/mocks.

## Risks / Trade-offs

- [Native request support can be advertised but broken] -> Explicit opt-in, no class-based inference, request/response conformance tests, and clear failure categories; LM Studio remains portable unless operators verify it.
- [Entry encoding increases output tokens and may affect extraction quality] -> Keep fixed schemas compact, retain original semantic instructions, and measure before enabling broadly. Do not claim net latency or cost savings.
- [Closed schemas do not prove facts or query safety] -> Keep all semantic validators, normalization, limit enforcement, and execution guards.
- [Recursive values or large integers are decoded incorrectly] -> Test nested maps/lists, nulls, duplicate names, integers beyond floating-point exactness, fractional values, and conversion limits; never stringify nested JSON as a shortcut.
- [Mode leaks into unaffected workflows] -> Per-request options and explicit regression assertions that discovery and other calls remain portable on a native-configured profile.
- [Captured model and live mode diverge] -> Resolve/capture one immutable binding and test profile edits, nested contexts, and exception restoration.
- [Provider errors contain prompts or generated content] -> Use sanitized classifications and fingerprints in touched paths; verify sentinel content cannot appear in logs, including failure paths.

## Migration Plan

1. Add the forward profile-column migration, default/backfill all existing profiles to `PORTABLE`, and propagate the non-secret enum through profile read/write/fact mappings.
2. Deploy mode-aware binding/capture and the two native adapters together. No existing profile automatically switches mode, no embedding/index migration occurs, and no existing run/snapshot/payload is rewritten.
3. Confirm default-profile seeding, old-client update omission, persistence restart, existing API shapes, and portable workflow regressions.
4. An operator explicitly enables `NATIVE_JSON_SCHEMA` for a tested provider/model profile. Document that schema discovery and other workflows remain portable on that same profile.
5. Roll back behavior by saving `PORTABLE`; newly resolved calls use the new revision, while already captured calls retain their existing binding. Leave the additive column intact. A binary downgrade must account for additive profile response fields and the recorded mode; do not silently remove the migration or rewrite historical state.

## References

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs): strict schema subset, refusals, recursive schemas, and remaining semantic mistakes.
- [Spring AI OpenAI chat documentation](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc): request-level response formats; syntax was additionally checked against the installed 2.1.0-M1 source JAR.
- `openspec/changes/archive/2026-07-15-fix-schema-draft-candidate-output/design.md`: project evidence for keeping portable discovery and rejecting model-class capability inference.
