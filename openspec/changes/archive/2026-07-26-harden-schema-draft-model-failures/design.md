## Context

Schema-draft analysis processes each prepared source through `DiscoverySourceAnalyzer` and `CandidateExtractionModelAdapter`. The OpenAI SDK owns transport retries inside `ChatModel.call`, while the application performs response presence checks, normal-content extraction, JSON conversion, and candidate validation only after that call returns.

The current failure classifier inspects only the outer exception type and message. Its broad `FailureCategory` also embeds one retryability value per category, even though failures within a category have different recovery semantics. For example, a stale source and an invalid model candidate are both validation-related, but only the latter can reasonably succeed after another model response. Partial runs also leave their persisted retryability flag at its default even when a failed source is retryable.

Normal application logs must remain metadata-first. Prompts, source text, model responses, reasoning content, candidate payloads, credentials, and raw provider messages are restricted from normal logs and may enter traces only through the existing controlled `AiObservationService` policy.

## Goals / Non-Goals

**Goals:**

- Produce stable, cause-aware failure decisions with category, detailed code, retryability, provider status, and bounded exception-chain metadata.
- Recover once from nondeterministic unusable model output without repeating transport failures already retried by the OpenAI SDK.
- Make partial-run retryability agree with its failed source outcomes.
- Preserve exact-match reuse of successful source outcomes.
- Add actionable response and attempt diagnostics without weakening content privacy.
- Keep existing broad failure categories compatible while exposing an additive detailed source failure code.

**Non-Goals:**

- Change source scheduling, introduce source-level parallelism, or enforce discovery source/request deadlines; those belong to `enforce-schema-draft-analysis-budgets`.
- Replace or duplicate the OpenAI SDK transport retry policy.
- Treat reasoning metadata as candidate output.
- Tune reasoning effort, verbosity, completion-token limits, or provider-specific output modes.
- Log every internal SDK HTTP attempt.

## Decisions

### Separate broad category, detailed code, and retryability

Introduce a structured failure decision containing:

- the compatible broad `FailureCategory`;
- a stable detailed code such as `TRANSPORT_TIMEOUT`, `RATE_LIMIT`, `PROVIDER_5XX`, `PROVIDER_AUTH`, `EMPTY_MODEL_RESPONSE`, `MALFORMED_MODEL_RESPONSE`, `INVALID_MODEL_CANDIDATE`, `SOURCE_STALE`, `SOURCE_UNAVAILABLE`, or `CONFIGURATION_ERROR`;
- an explicit retryable flag;
- optional provider HTTP status;
- bounded outer-to-root exception type names and root exception type;
- a non-reversible terminal-message fingerprint.

The classifier walks a bounded cause chain, recognizes OpenAI SDK service/I/O exceptions and standard timeout causes explicitly, and uses sanitized message matching only as a compatibility fallback. Broad categories remain in existing API fields. Per-source results gain an additive detailed failure code; legacy records without it remain readable.

Alternative considered: add more values to `FailureCategory` and continue deriving retryability from the enum. Rejected because category compatibility and retry policy evolve at different rates, and one category can contain both retryable and permanent failures.

### Retry only unusable successful responses at the application layer

Add at most one additional model-output attempt for:

- missing result or assistant message;
- blank normal assistant content;
- malformed candidate JSON;
- converted candidate output that violates the candidate contract.

Transport, rate-limit, and provider-service exceptions are not retried again by this loop because the SDK has already applied the profile's `maxRetries`. Each output attempt creates its own model observation. Candidate collections are appended only after conversion and validation succeed, preventing data from a failed attempt from leaking into the aggregate.

Candidate contract validation must execute inside the output-attempt boundary. This may be achieved by extracting a validator from the current candidate mapping path or by keeping mapping and validation inside a retryable chunk attempt. Failures are represented by typed, content-safe exceptions rather than inferred solely from free-form messages.

Alternative considered: retry every `RuntimeException` around source analysis. Rejected because it would multiply exhausted transport retries, repeat permanent source/configuration failures, and potentially redo already accepted chunks.

### Carry safe attempt context and response diagnostics

Pass an immutable attempt context through source analysis containing nullable draft/run identifiers plus source, source revision, chunk, profile revision, and attempt counters as applicable. The adapter extracts a safe response diagnostic snapshot before conversion:

- response ID and model;
- finish reason;
- prompt, completion, and total token counts;
- trimmed normal-content length and fingerprint;
- reasoning-content presence and length without its value.

When output is unusable, the typed exception carries only this diagnostic snapshot, never response content. Successful and failed model observations receive the same safe metadata. Normal logs use identifiers, counts, classifications, fingerprints, and timings only.

Alternative considered: log the raw provider response or exception message at debug level. Rejected because normal logging is intentionally independent of trace content-capture controls and provider messages can contain request or response content.

### Derive terminal run retryability from source outcomes

For both `FAILED` and `PARTIAL` terminal runs, persisted retryability is true when at least one failed source outcome is retryable. A fully successful run remains non-retryable. The retry endpoint continues to create a new captured snapshot, reuse exact-match successful results, and execute unresolved sources.

Run-level `failureCategory` remains reserved for aggregate/run failures. Per-source failure category/code remains the authoritative reason for a valid partial aggregate.

### Distinguish logical output attempts from SDK transport attempts

Fields and messages call the new counter `outputAttempt` rather than `providerAttempt` or `httpAttempt`. A `ChatModel.call` is one logical output attempt even when the OpenAI SDK performs multiple internal HTTP attempts. Logs include the configured profile timeout and SDK retry count as configuration metadata, but do not claim to observe internal attempts.

If per-HTTP-attempt visibility is later required, it can be added through Spring AI's OpenAI HTTP-client builder customizer as a separate observability change.

## Risks / Trade-offs

- [An invalid-output retry increases latency and token usage] → Allow only one extra output attempt and never apply it to exhausted transport failures.
- [A typed classifier can become coupled to OpenAI SDK exception classes] → Isolate provider-specific recognition behind a shared failure classifier and retain provider-neutral fallbacks.
- [Detailed failure codes add an API/persistence field] → Make the field additive and nullable for legacy results; retain existing broad categories.
- [Response identifiers and exception chains are high-cardinality] → Keep them out of low-cardinality metric tags and place them only in logs or high-cardinality observations.
- [Message fingerprints may differ across equivalent failures] → Use the stable detailed code for aggregation and the fingerprint only for correlation.
- [Candidate validation currently occurs outside the model adapter] → Refactor validation into the retryable chunk boundary and add tests proving failed-attempt candidates are never accumulated.

## Migration Plan

1. Add typed failure decisions, detailed codes, safe exception-chain extraction, and compatibility reads for legacy outcomes.
2. Add safe model-response diagnostics and propagate attempt context through candidate extraction.
3. Add the bounded invalid-output retry around conversion and candidate validation.
4. Correct terminal run retryability and expose the detailed source code additively.
5. Deploy with existing profile timeout/retry settings; no backfill is required.
6. Roll back by disabling the application output retry and detailed-code emission while retaining nullable persisted properties. Existing successful result reuse remains valid unless the candidate/prompt contract changes; if prompt semantics change during implementation, increment the appropriate contract revision.

## Open Questions

None. The first implementation exposes the same additive detailed source failure code in synchronous discovery outcomes and durable draft outcomes while preserving their established envelopes.
