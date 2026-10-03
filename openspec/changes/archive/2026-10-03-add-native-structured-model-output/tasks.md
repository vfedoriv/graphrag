## 1. Profile mode and relational compatibility

- [x] 1.1 Add the AI-owned `StructuredOutputMode` enum and optional `structuredOutputMode` to create/update DTOs, profile domain values, non-secret `ProfileFacts`/`ProfileView`, profile read responses, and KB profile configuration mappings; verify API/service tests cover portable creation defaults, explicit native selection, invalid-value rejection, omitted/null update retention, and write-only keys.
- [x] 1.2 Add a new forward Flyway migration and entity/repository mappings for a non-null, allowed-value-constrained mode defaulting to `PORTABLE`; verify fresh provisioning and migration of existing profiles preserve IDs, secrets, revisions, defaults, assignments, and persisted modes after reload.
- [x] 1.3 Include mode changes in existing revision/cache invalidation semantics and retain embedding compatibility checks; verify mode-only edits with stored embeddings succeed, incompatible combined edits fail atomically, and existing default-selection/concurrency tests pass.
- [x] 1.4 Keep startup default seeding portable and preserve an existing saved mode; verify startup/profile bootstrap tests cover both absent and existing default profiles without adding provider calls or raw model-setting overrides.

## 2. Consistent resolved and captured model configuration

- [x] 2.1 Add an immutable resolved chat binding under `ai.models` carrying model, mode, and profile ID/revision from one profile load; retain plain model capabilities for unaffected callers and verify cache/resolver tests demonstrate revision-consistent mode/model pairs and portable fallback for unscoped custom beans.
- [x] 2.2 Extend AI-owned captured execution/context and public scoped resolution to capture the whole binding; verify `CapturedAiExecutionTest` and resolver tests cover profile changes after capture, nested profiles/modes, restoration on exceptions, and removal after scope exit.
- [x] 2.3 Route the graph extraction and Cypher adapters through the public mode-aware resolver without introducing feature dependencies on AI persistence/private provider context; verify architecture tests and existing fallback/active-profile adapter tests pass.

## 3. Strict native wire contracts and conversion

- [x] 3.1 Define the graph adapter's fixed native JSON Schema and recursive value/entry representation for properties and endpoint keys, with closed objects, required fields, nullable confidence, and an object root; verify schema-contract tests check every object, recursive reference, workflow version, and top-level shape against the supported provider subset.
- [x] 3.2 Define the Cypher adapter's fixed native JSON Schema and equivalent private recursive value/entry representation for parameters; verify schema-contract tests cover required Cypher/explanation/parameters fields and exclude unconstrained objects or top-level unions.
- [x] 3.3 Implement graph native decoding back to existing `GraphExtractionResult` maps/collections; verify conversion fixtures cover empty extraction, typed values, composite endpoint keys, nested maps/lists, explicit nulls, nullable confidence, exact large integers, duplicate names/fields, invalid structures, trailing text, and existing unknown-field warning tolerance.
- [x] 3.4 Implement Cypher native decoding back to `GeneratedCypher` and its ordinary parameter map; verify equivalent value fixtures, invalid/duplicate-entry rejection, and unchanged public query response JSON with no provider-envelope leakage.
- [x] 3.5 Add native prompt instructions matching the entry encoding while retaining active-schema and allowed-triple/omit rules; verify captured prompt tests and portable regression fixtures show that portable prompts/parsers retain their existing contracts.

## 4. Request enforcement and explicit failure handling

- [x] 4.1 Attach per-call `JSON_SCHEMA` response formats with the response-format-specific `strict(true)` only for native graph/Cypher bindings; verify real Spring AI requests against a local HTTP stub contain the expected schema/strict flag and retain profile model options, while portable requests omit native formatting.
- [x] 4.2 Add native completion/refusal/empty-content checks before conversion and safe diagnostic categories; verify stubbed SDK responses map refusal metadata and completion reasons correctly, including parseable text on refusal/truncation and JSON present only in reasoning.
- [x] 4.3 Detect local inability to encode native formatting and safely classify explicit provider format rejection without conflating generic HTTP 400, authentication, throttling, or timeout errors; verify provider-error fixtures preserve configured SDK retry behavior and never send a portable fallback or mutate the saved mode.
- [x] 4.4 Add mode, output-contract version, and safe outcome diagnostics through existing observations; verify success/failure sentinel tests exclude prompts, source/model/refusal/reasoning content, parameter values, schema payloads, and raw provider messages from ordinary logs while controlled trace capture retains its existing settings.

## 5. Workflow safety and compatibility regression coverage

- [x] 5.1 Exercise accepted native graph output through existing extraction normalization/filtering/limit paths; verify invalid labels/triples/endpoints are filtered or repaired as specified, unknown fields are tolerated, and fatal limits or rejected native output produce no graph persistence.
- [x] 5.2 Exercise native Cypher generation through generation and `/ask` validation/execution paths, including callers reusing that client; verify blocked statements, invalid schema references, excessive limits, and planner failures prevent execution and preserve existing response/error contracts.
- [x] 5.3 Run schema discovery/draft candidate analysis and the other portable model adapters with a native-configured profile; verify no native schema leaks into candidate extraction, schema generation, planning, reranking, sufficiency, or synthesis requests, and existing bounded attempt/deadline semantics remain intact.
- [x] 5.4 Run `./mvnw test -Pfast` to verify deterministic adapter, profile, observation, and architecture regressions together; run the credential-free `./mvnw test` with escalated execution for Testcontainers to verify relational migration, workflow integration, and existing end-to-end coverage. Record any environment limitation without claiming unavailable checks passed.

## 6. Documentation and rollout verification

- [x] 6.1 Update `src/site/markdown/workflows/knowledge-bases-profiles.md`, `document-processing.md`, and `cypher-queries.md` with mode defaults, update omission semantics, opt-in compatibility limits, native failure behavior, scope, and rollback; verify examples retain public map shapes and make no unmeasured quality/cost/latency claim.
- [x] 6.2 Synchronize overlapping implementation facts in `README.md`, `AGENTS.md`, and `CLAUDE.md` and add a portal navigation entry only if a new page is needed; verify `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` and `./mvnw site` pass.
- [x] 6.3 Document a manual optional comparison recipe using the same provider/model, prompts/corpus, timeout, and retry settings across modes, reporting parse failure, semantic acceptance/drop counts, latency, and token usage; verify the delivered recipe distinguishes required stubbed conformance coverage from optional credentialed provider measurements and identifies explicit `PORTABLE` rollback.
- [x] 6.4 Validate the final OpenSpec change with `openspec validate add-native-structured-model-output --strict` and reconcile checked tasks with the verified implementation; completion requires a clean validation result and evidence for the required checks above.
