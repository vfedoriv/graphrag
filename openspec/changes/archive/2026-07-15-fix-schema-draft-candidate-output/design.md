## Context

Candidate extraction is the only backend chat call that currently attaches provider-native JSON Schema options. `DiscoverySourceAnalyzer` already prepares both a native prompt and a portable prompt containing `BeanOutputConverter.getFormat()`, while `CandidateExtractionModelAdapter` selects the native request whenever the resolved model is an `OpenAiChatModel`.

That class check identifies an OpenAI-compatible client, not a provider/model capability. Live verification against the configured LM Studio Qwen reasoning model showed that the native request returned valid candidate JSON in `reasoning_content` and empty normal content. The same model returned valid candidate JSON in normal content when given a prompt-only portable request. Existing schema-example and Cypher endpoints also succeeded through their prompt-only response paths.

## Goals / Non-Goals

**Goals:**

- Make candidate extraction interoperable with OpenAI-compatible providers and reasoning models by using the existing portable structured-output path.
- Preserve typed conversion and deterministic candidate validation before aggregation.
- Keep reasoning metadata separate from final model output.
- Make failed source outcomes and logs accurately describe work completed before failure without exposing content.
- Remove native-output branching that is not backed by an explicit provider/model capability contract.

**Non-Goals:**

- Add provider-native structured-output capabilities to AI profiles.
- Add a generic `reasoningContent` fallback to every chat client.
- Change other working LLM call chains, HTTP contracts, persistence shapes, or frontend behavior.
- Optimize reasoning-model latency or token use in this change.

## Decisions

### Always use the portable candidate prompt

`DiscoverySourceAnalyzer` will build one candidate prompt containing `BeanOutputConverter.getFormat()`, and `CandidateExtractionModelAdapter` will call the resolved `ChatModel` with a plain `Prompt`. The adapter will no longer inspect the model class or construct `OpenAiChatOptions`.

This matches the provider-independent request pattern already used by schema generation, graph extraction, and Cypher generation. It also follows Spring AI's default structured-output approach when provider-native support is not explicitly enabled.

Alternative considered: retain native output and read `reasoningContent` when normal content is empty. Rejected because reasoning metadata is not a final-answer contract, plain calls contain verbose internal reasoning there, and the fallback would preserve an unreliable provider-specific path.

Alternative considered: add an AI-profile structured-output capability. Deferred because the verified fix does not require a new public configuration contract, and there is no current need to opt any candidate-extraction profile back into native mode.

### Convert only normal assistant content

The adapter will require a response result and assistant message, trim normal assistant text, and pass it to `BeanOutputConverter<CandidateExtractionResult>`. Missing, blank, or malformed normal content will fail conversion and remain a per-source failure. Reasoning metadata will not be inspected.

Conversion remains followed by existing candidate mapping and validation, so prompt-based formatting does not allow invalid candidates to enter aggregation.

### Preserve preparation progress on failure

Per-source analysis will retain the prepared source reference or its chunk count across the model-analysis try/catch boundary. If preparation succeeded, a later failure will persist the prepared chunk count; a failure before preparation completes will persist zero.

### Add content-safe source failure diagnostics

The per-source warning will include draft/run/source identifiers, failure category, retryability, prepared chunk count, exception type, and `LogMetadata.exceptionMessageFingerprint(exception)`. It will not include exception messages, prompts, source text, normal or reasoning output, or candidate payloads.

## Risks / Trade-offs

- [Portable output is not provider-enforced] → Keep `BeanOutputConverter` conversion and existing candidate validation as hard acceptance boundaries; malformed output remains isolated to the source outcome.
- [Reasoning models may use more tokens and take longer] → Accept the verified interoperability trade-off now and retain existing runtime timeouts and bounded concurrency; performance tuning can be proposed separately.
- [Removing native mode may reduce performance for providers that support it correctly] → Prefer one reliable cross-provider contract until a concrete capability and conformance-test requirement justifies reintroducing native mode.
- [Failure logs could leak content through exception messages] → Log only exception type and a non-reversible message fingerprint.

## Migration Plan

No data migration is required. Deploy the portable request path and tests together. Existing completed source results remain reusable under the current prompt/candidate revision rules; if the implementation changes the prompt contract text, increment the prompt revision so old and new results are not conflated. Rollback restores the prior adapter branch but also restores the confirmed LM Studio failure.

## Open Questions

None for implementation. Provider-native candidate output can be reconsidered only as a separate, capability-driven change with provider conformance tests.
