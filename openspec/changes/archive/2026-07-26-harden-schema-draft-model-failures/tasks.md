## 1. Structured Failure Decisions

- [x] 1.1 Add stable detailed source failure codes and a structured failure-decision type containing broad category, code, retryability, provider status, bounded exception-chain types, root type, and message fingerprint.
- [x] 1.2 Implement a shared cause-chain classifier for OpenAI SDK I/O/service exceptions, standard timeout causes, HTTP rate-limit/service/permanent statuses, typed model-output failures, source-state failures, and configuration failures.
- [x] 1.3 Replace message-only classification in synchronous discovery and durable draft analysis with the shared classifier while retaining compatibility fallbacks.
- [x] 1.4 Add focused classifier tests for nested timeouts, 408/409/429/5xx statuses, permanent 4xx statuses, empty/malformed output, invalid candidates, stale/unavailable sources, configuration errors, and bounded/cyclic cause chains.

## 2. Candidate Output Recovery

- [x] 2.1 Introduce typed content-safe exceptions and safe response diagnostics for missing results/messages, blank normal content, malformed conversion, and candidate-contract validation.
- [x] 2.2 Move or wrap candidate-contract validation so model call, response extraction, conversion, and validation form one retryable chunk attempt without accumulating failed-attempt candidates or aliases.
- [x] 2.3 Add one application-level retry only for unusable completed model output, with no additional retry for transport/provider/configuration/source-state failures.
- [x] 2.4 Preserve the rule that reasoning metadata is diagnostic metadata only and is never accepted as candidate output.
- [x] 2.5 Add adapter/analyzer tests for invalid-then-valid output, two invalid outputs, transport failure without application retry, permanent failure without retry, and isolation of failed-attempt data.

## 3. Durable Outcomes and API Contracts

- [x] 3.1 Add a nullable detailed failure code to durable source results and synchronous discovery outcomes while preserving existing broad category fields and compatibility reads for legacy records.
- [x] 3.2 Persist `PARTIAL` and `FAILED` run retryability from retryable failed source outcomes and keep fully successful runs non-retryable.
- [x] 3.3 Verify that retrying an unchanged partial run reuses matching successful results and executes only unresolved eligible sources.
- [x] 3.4 Update DTO/OpenAPI contract tests and schema-draft lifecycle integration tests for additive failure codes, partial retryability, legacy null codes, and reuse behavior.

## 4. Privacy-Safe Diagnostics and Observability

- [x] 4.1 Add immutable attempt context carrying safe draft/run/source/chunk/profile and logical output-attempt metadata through candidate extraction.
- [x] 4.2 Extract response ID/model, finish reason, token usage, normal-content length/fingerprint, and reasoning-content presence/length from Spring AI responses without retaining response content in diagnostic objects.
- [x] 4.3 Enrich model observations with safe attempt/response/failure attributes and keep high-cardinality identifiers out of metric tags.
- [x] 4.4 Emit content-safe retry and terminal-failure warnings with elapsed time, classifications, provider status, configured timeout/retry metadata, bounded exception types, and message fingerprints.
- [x] 4.5 Add observability and captured-log tests proving diagnostics are present while prompts, sources, model output, reasoning, candidate payloads, raw exception messages/provider bodies, headers, and credentials remain absent.

## 5. Verification

- [x] 5.1 Run focused candidate extraction, discovery service, schema-draft lifecycle, DTO/OpenAPI, observability, and logging tests.
- [x] 5.2 Run `./mvnw test`.
- [x] 5.3 Run `graphify update .` and review the updated failure/retry paths.
