## Why

Schema-draft source analysis currently conflates transport failures, provider responses, conversion errors, and source validation failures, which produces misleading retryability and weak diagnostics. Blank or malformed model output is detected only after the OpenAI SDK has finished, so otherwise reusable partial runs can require repeated manual analysis without a bounded application-level recovery attempt.

## What Changes

- Introduce structured, cause-aware model failure decisions with a stable detailed failure code, compatible broad failure category, and independently determined retryability.
- Retry missing, blank, malformed, or candidate-contract-invalid model output once at the application layer without repeating transport failures already exhausted by the provider SDK.
- Mark partial analysis runs retryable whenever at least one failed source outcome is retryable, while preserving successful source-result reuse.
- Capture response metadata and privacy-safe per-attempt diagnostics, including elapsed time, response identifiers, model, finish reason, token usage, content lengths/fingerprints, bounded exception-chain types, root exception type, and provider status code when available.
- Preserve existing restrictions against logging prompts, source content, normal or reasoning model output, candidate payloads, credentials, or raw provider exception messages.
- Add focused regression coverage for classification, retry boundaries, partial-run retryability, result reuse, response metadata, and log privacy.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-analysis`: Define detailed failure decisions, bounded invalid-output recovery, correct partial-run retryability, and reusable successful outcomes after retry.
- `multi-source-schema-discovery`: Define retry behavior for unusable model output and distinguish model-contract failures from source validation and transport failures.
- `ai-observability-monitoring`: Require privacy-safe response and attempt metadata for schema-discovery model calls.
- `privacy-safe-operational-logging`: Require actionable bounded cause/status diagnostics without exposing model or source content.

## Impact

- Candidate extraction and validation in `CandidateExtractionModelAdapter` and `DiscoverySourceAnalyzer`.
- Per-source failure handling, run completion, and retry metadata in `SchemaDraftAnalysisService`.
- Shared failure classification and safe logging helpers.
- AI model-call observation attributes and token/response metadata extraction.
- Schema-draft analysis DTOs and OpenAPI output if the detailed failure code is exposed to clients; existing failure categories remain compatible.
- Unit, integration, observability, logging-privacy, and API-contract tests.
- No persistence migration or frontend change is required for the backend to retain existing broad categories; exposing the detailed code may require additive DTO and frontend handling.
