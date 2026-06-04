## Context

The application is a Java 25 / Spring Boot 4 service where Spring AI owns provider runtime beans for chat and embeddings, while LangChain4j is used for graph transformer utilities and routed through local adapters. Current AI visibility is mostly log-based: model call starts, previews, elapsed time, and parse outcomes are written to application logs, but there is no durable trace view that connects document processing, embeddings, graph extraction, schema generation, Cypher generation, validation, and execution.

Langfuse is a good fit for LLM observability because it now supports OpenTelemetry ingestion and provides trace, generation, usage, cost, prompt, and evaluation views for LLM applications. For this codebase, OpenTelemetry should be the integration boundary: it keeps the application backend-agnostic, fits Spring AI observability, and avoids tying production code to a Langfuse-only Java SDK.

Self-hosted Langfuse is not a single auxiliary service. Current Langfuse v3 local deployment requires Langfuse web and worker services plus Postgres, ClickHouse, Redis, and object storage such as MinIO. This should therefore be optional and profile-gated in Compose.

## Goals / Non-Goals

**Goals:**

- Emit OpenTelemetry traces for all model-facing workflows.
- Emit lightweight Micrometer metrics for model calls, failures, latency, and token usage when available.
- Correlate low-level chat and embedding operations with higher-level application workflows.
- Export to self-hosted Langfuse during local development and to any compatible OTLP backend in other environments.
- Keep AI observability disabled or no-op by default unless configured.
- Make Langfuse trace input/output useful for local debugging by capturing full prompt and response content by default, while allowing that behavior to be disabled and length-capped.
- Document local Langfuse setup and verification.

**Non-Goals:**

- Replace existing application logs.
- Add a new public API for traces or monitoring data.
- Store trace data in Neo4j.
- Require Langfuse in default development startup or tests.
- Implement automated LLM evaluation, prompt management, or production SLO dashboards in the first implementation.

## Decisions

### Use OpenTelemetry as the application contract

Add Spring Boot Actuator, Micrometer tracing bridge, OpenTelemetry instrumentation, and OTLP exporter configuration where needed. Use explicit spans around application workflows and let Spring AI/OpenTelemetry instrumentation capture provider calls when available.

Alternatives considered:

- Langfuse Java client only: rejected as the primary path because the app already has Spring AI instrumentation hooks and OTel keeps backend choice open.
- Logs-only monitoring: rejected because logs do not provide trace hierarchy, generation views, or usage/cost aggregation.
- Provider-native dashboards only: rejected because the app supports OpenAI-compatible providers, including local LM Studio.

### Add an `AiObservationService` facade

Introduce a small application service that creates spans and attaches attributes consistently. Model-facing services call it for workflow spans and operation annotations instead of directly scattering OpenTelemetry attribute keys throughout the codebase.

The facade should support:

- `enabled` flag.
- content capture flag.
- sanitized prompt/response previews and lengths.
- workflow attributes such as document id, knowledge base id, schema id, extraction run id, model name, provider profile, chunk count, and result counts.
- error recording with sanitized messages.
- Micrometer counters and timers for model call attempts, successes, failures, latency, and token usage when provider metadata exposes token counts.

Alternatives considered:

- Instrument each service directly with OpenTelemetry APIs: rejected because it would duplicate keys and privacy decisions.
- AOP-only instrumentation: rejected for the first version because important domain metadata is easiest and clearest at call sites.

### Add lightweight Micrometer metrics in the first implementation

Record application-level metrics alongside traces so operators can alert and dashboard without querying trace payloads. Metrics should use low-cardinality tags such as operation, workflow, model name when bounded by configuration, provider profile, and status. They must not tag by document id, user query, prompt hash, schema id, extraction run id, or other high-cardinality values.

Initial metrics should cover:

- model call count by operation and status.
- model failure count by operation and sanitized failure category.
- model latency timers by operation and status.
- input, output, and total token counters when usage metadata is available.

Alternatives considered:

- Tracing only in the first release: rejected because metrics are needed for lightweight health monitoring, alerting, and trend views.
- Full custom cost dashboard in the first release: rejected because provider usage metadata can be incomplete, and cost mapping should be a later focused change.

### Treat only low-cardinality trace attributes as dashboard-stable

Future dashboards and alerts should rely on a small stable attribute set that is unlikely to leak sensitive data or explode cardinality:

- `ai.operation`: `chat`, `embedding`, or another bounded model operation category.
- `ai.workflow`: bounded application workflow name such as `document-processing`, `graph-extraction`, `schema-generation`, or `cypher-generation`.
- `ai.provider.profile`: active model profile such as `openai` or `lm_studio`.
- `ai.model.name`: configured model name, with an option to disable or bucket this if deployments use unbounded model names.
- `ai.status`: `success` or `failure`.
- `ai.failure.category`: sanitized bounded failure category such as `provider_error`, `timeout`, `parse_error`, `validation_error`, or `configuration_error`.
- `ai.content_capture`: whether full content capture was enabled for the span.
- `ai.schema.name`: schema name only when bounded enough for the deployment; schema ids and versions remain diagnostic trace attributes, not alert dimensions.

The following attributes may be useful inside individual traces but should not be treated as dashboard-stable or metric tags: document id, knowledge base id, schema id, extraction run id, prompt hash, prompt text, response text, natural-language query, chunk text, and raw exception message.

Alternatives considered:

- Include domain identifiers as stable attributes: rejected because they are high-cardinality and better suited for trace lookup than dashboards.
- Make only generic OTel attributes stable: rejected because AI-specific workflows need operation, model, provider profile, and failure dimensions to be actionable.

### Instrument centralized model paths first

Primary instrumentation points:

- `SpringAiEmbeddingClient` for embedding batches.
- `SpringAiGraphExtractionClient` for graph extraction chat calls.
- `SpringAiCypherGenerationClient` for Cypher generation chat calls.
- `SpringAiLangChain4jChatModelAdapter` for LangChain4j schema generation routed through Spring AI.
- Workflow services such as `DocumentProcessingService`, `GraphExtractionService`, `LangChain4jSchemaGenerationService`, and `QueryService` or equivalent query orchestration points for parent spans.

This gives broad coverage without adding provider-specific LangChain4j model dependencies, preserving the existing model ownership guard.

### Separate Langfuse input/output capture from generic content metadata

By default, Langfuse-compatible trace input/output fields include full prompt, natural-language query, and model response content up to a dedicated input/output length limit. This keeps local Langfuse traces useful for debugging prompt behavior. A separate property disables full input/output capture and falls back to sanitized previews plus length/hash metadata.

Generic high-cardinality metadata remains privacy-oriented by default: exported spans include lengths, counts, hashes/previews, schema names/ids, model configuration names, timings, and statuses. Separate full `*.content` metadata attributes are still exported only when generic content capture is explicitly enabled.

### Add Langfuse as an optional compose profile

Extend `compose.yaml` with a profile such as `langfuse` containing:

- `langfuse-web`
- `langfuse-worker`
- `langfuse-postgres`
- `langfuse-clickhouse`
- `langfuse-redis`
- `langfuse-minio`

Use local volumes for Postgres, ClickHouse, ClickHouse logs, and MinIO. Bind exposed ports to localhost where practical. Keep secrets configurable through environment variables and provide development defaults only for local use.

The application exports to Langfuse through OTLP configuration:

- endpoint similar to `http://localhost:3000/api/public/otel` for local Langfuse.
- headers including Basic auth derived from Langfuse public/secret keys and Langfuse ingestion version when required.

### Auto-create local Langfuse project and API keys

The local Langfuse Compose profile should initialize a development organization, project, user, and project API keys through environment variables. This makes local verification deterministic: developers can start the profile, use the documented dev public/secret key pair, and immediately export traces without first navigating the Langfuse UI.

Use obvious local-only defaults for values such as organization name, project name, user email/password, public key, and secret key. Documentation must state that these defaults are only for local development and must not be reused in shared or production environments.

Alternatives considered:

- Manual key creation in the Langfuse UI: rejected for local development because it adds setup friction and makes smoke-test instructions depend on per-developer UI steps.
- Production-like secret provisioning for local Compose: rejected because this change targets local development observability and should not complicate the default developer workflow.

## Risks / Trade-offs

- [Risk] Self-hosted Langfuse increases local resource usage substantially -> Mitigation: keep it behind an explicit compose profile and document the lighter default workflow.
- [Risk] Spring AI 2.0 milestone observability APIs may shift -> Mitigation: keep direct dependencies small and put custom application metadata behind `AiObservationService`.
- [Risk] Full prompts or chunks could leak into traces -> Mitigation: provide an input/output capture disable switch, cap exported input/output length, keep generic content capture off by default, and document privacy implications for shared environments.
- [Risk] Token usage and cost may not be available for every OpenAI-compatible provider -> Mitigation: record usage when present and avoid fabricating token/cost values when provider metadata is missing.
- [Risk] OTel exporter failures could affect AI workflows -> Mitigation: configure exporter behavior so trace export failures are non-fatal and covered by startup/runtime tests.
- [Risk] Langfuse defaults in Compose are not production-safe -> Mitigation: label them as local-development defaults and require real secrets for shared environments.
- [Risk] Auto-created local Langfuse credentials could be copied into shared environments -> Mitigation: use clearly named dev-only values and document that shared environments must provision independent Langfuse credentials.

## Migration Plan

1. Add observability dependencies and configuration properties with disabled/no-op defaults.
2. Implement `AiObservationService` and focused unit tests for enabled, disabled, content capture, metric recording, and error cases.
3. Add workflow and model operation spans to the centralized AI paths.
4. Add optional Langfuse services and volumes to `compose.yaml`.
5. Update README and profile guidance with local setup, OTLP headers, and privacy controls.
6. Run the test suite and manually verify that an AI-enabled local profile can export at least one trace to Langfuse.

Rollback is straightforward: disable AI observability configuration to stop exports. If infrastructure changes cause local issues, omit the Langfuse compose profile and continue using Neo4j-only startup.

## Resolved Questions

- The first implementation will include lightweight Micrometer counters and timers for model calls, failures, latency, and token usage when usage metadata is available, alongside tracing.
- Future dashboards and alerts should rely on low-cardinality attributes: `ai.operation`, `ai.workflow`, `ai.provider.profile`, bounded `ai.model.name`, `ai.status`, `ai.failure.category`, `ai.content_capture`, and optionally bounded `ai.schema.name`.
- Local Langfuse startup will auto-create a development organization, project, user, and project API keys through environment variables, using local-only defaults.
