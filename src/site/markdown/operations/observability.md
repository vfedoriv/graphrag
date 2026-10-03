# AI observability, logging, and privacy

GraphRAG separates normal application logging from AI observation content. Logs are metadata-first at every level; only the central observation path may attach controlled AI inputs/outputs to traces.


Generic workflow/model observations stay under `observability` and consume typed
settings plus non-secret startup model metadata. Search-specific outcome and
terminal metrics live in `search.runs.adapters.metrics.AdvancedSearchMetrics`.
The move preserves metric names/tags/counts and content-capture behavior;
metadata-only logging remains governed by `logging.LogMetadata`.

## Signals

`AiObservationService` creates workflow spans and model-call observations for embedding, extraction, schema generation/discovery/drafts, Cypher generation, advanced search, and related AI stages. Micrometer records call count, failure, latency, model/provider metadata, and token usage when the provider reports it. OpenTelemetry exports spans when tracing and an OTLP exporter are enabled.

Default startup has AI observability and tracing disabled and requires no Langfuse endpoint.

## Content controls

| Setting | Meaning |
|---|---|
| `app.ai.observability.enabled` | Enables application-owned AI workflow/model observations. |
| `input-output-content-enabled` | Allows full observation Input/Output content, capped by the configured maximum. |
| `content-capture-enabled` | Allows separate full content attributes in application-owned observation metadata. |
| `max-input-output-length` / `max-attribute-length` | Bound exported content and attribute sizes. |
| model/schema tag flags | Control selected identifying tags. |

The local `langfuse` profile enables observation/tracing and full Input/Output capture for local debugging. Do not enable full prompt/document/query/response capture in shared or production environments unless retention, access, and data-classification policy explicitly permit it. Disable Input/Output content and leave separate content capture off for metadata-only traces.

## Metadata-first application logs

Normal logs may contain workflow/entity IDs, revisions, byte/character lengths, counts, timings, statuses, exception classes, and short non-reversible fingerprints. They must not contain document/source text, prompts, user queries, model responses, generated schemas, candidate projections, or extracted graph payloads. Raising `logging.level.root` does not enable a content diagnostic mode.

`org.springframework.ai.openai.OpenAiChatModel` logging defaults to `OFF` because
its SDK diagnostics can print prompts (for example, an empty choices response) or
provider payloads. Keep that deployment logger disabled and use application-owned
model-call observations for safe outcomes and controlled trace content instead.

The trace capture settings affect observations, not ordinary log statements. Use correlation IDs/fingerprints to locate the opt-in trace, then apply its retention and access policy.

## Local Langfuse and Garage

```bash
docker compose --profile langfuse up -d

OPENAI_API_KEY=<key> ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=openai,langfuse
```

The profile starts Langfuse web/worker, PostgreSQL, ClickHouse, Redis, and pinned Garage `v2.3.0`. Garage initialization is idempotent and creates the scoped bucket/key before Langfuse becomes ready. Event uploads use the internal Compose endpoint; presigned media URLs use the host-reachable endpoint (local default `http://localhost:9090`).

Local UI defaults are intended only for development. After startup, verify `http://localhost:3000`, an exported workflow span, model-call attributes/tokens, and a media upload/download whose presigned URL uses the host-reachable Garage endpoint.

## Privacy checklist

- Keep provider/API/database/Garage/OTLP secrets outside version control and redact read APIs.
- Classify uploaded content and prompts before enabling capture.
- Bound capture size, sampling, retention, and access.
- Confirm application logs remain content-free under DEBUG/TRACE.
- Back up both Garage metadata and data volumes together; neither alone is a valid media backup.

Implementation: `AiObservationService`, model-client adapters, `AdvancedSearchMetrics`, `application-langfuse.properties`, and `compose.yaml`.
