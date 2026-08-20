# Configuration and runtime settings

GraphRAG has three configuration layers: startup properties/environment, PostgreSQL-backed runtime overrides, and PostgreSQL-backed AI profiles. The API exposes metadata for all relevant groups, but only allowlisted values are editable.

## Startup profiles

| Profile | Behavior |
|---|---|
| `default` | Chat/embedding model autoconfiguration disabled; no provider credential needed to boot. |
| `openai` | Enables Spring AI OpenAI-compatible chat and embeddings; expects `OPENAI_API_KEY`. |
| `lm_studio` | Enables the configured LM Studio-compatible endpoint; `LM_STUDIO_API_KEY` defaults to the local placeholder. |
| `langfuse` | Enables AI observation and OTLP export to the local Langfuse defaults; combine with an AI profile. |

Effective `app.model.*` properties seed a persisted default AI profile only when none exists. After seeding, use the AI profile API for provider/model behavior; raw `app.model.*` and derived `spring.ai.openai.*` values are profile-managed context.

## Runtime-setting lifecycle

`GET /api/v1/runtime-settings` returns catalog metadata including `key`, typed values, source, `mutable`, `liveApplied`, `updateMode`, `activeValue`, `lifecycleState`, sensitivity, and a reason when edits are rejected or need restart.

- **Live mutable**: validated updates apply immediately to subsequent requests/attempts. This includes query, advanced-search, chunking, extraction, schema-discovery, AI-observability controls, and `logging.level.root` through Spring Boot logging.
- **Restart-required mutable**: safe non-secret desired values such as `app.storage.documents-root` are persisted, but `activeValue` stays at the startup value and `lifecycleState` is `pending-restart` until a backend restart loads it.
- **Read-only / deployment-managed**: application identity, auto-configuration switches, datasource pool and schema, Neo4j connectivity, multipart, actuator/health, tracing, and exporter settings are visible where useful but not reassigned in a running process.
- **Profile-managed**: provider URL/model/dimensions and derived Spring AI aliases are changed through AI profiles.
- **Sensitive read-only**: provider keys, datasource/Neo4j passwords, and OTLP authorization headers expose configured/masked status only.

`mutable=true` means the settings API accepts an update or clear; it does not mean the value is already active. Read `liveApplied`, `updateMode`, `activeValue`, and `lifecycleState` together.

Update one key with `PUT /runtime-settings/{key}`, update a validated set atomically with `PUT /runtime-settings`, and clear an override with `DELETE /runtime-settings/{key}`. Feature services consume typed accessors from `RuntimeSettingsService`; ad hoc string reads are not part of the contract.

## Current processing defaults

The source of truth is `src/main/resources/application.properties`. Important local defaults include:

```properties
app.storage.documents-root=./var/documents
app.chunking.strategy=recursive
app.chunking.target-tokens=800
app.chunking.overlap-tokens=80
app.chunking.hard-character-limit=4000
app.chunking.parent-target-tokens=1600
app.query.max-rows=200
app.query.timeout-seconds=15
app.query.require-limit=true
app.advanced-search.default-evidence=10
app.advanced-search.max-evidence=20
app.advanced-search.deadline-seconds=60
app.extraction.max-entities-per-chunk=100
app.extraction.max-relationships-per-chunk=200
app.extraction.max-retries=2
```

Canonical chunking keys are `strategy`, `target-tokens`, `overlap-tokens`, and `hard-character-limit`. `max-tokens` and `max-characters` remain compatibility aliases; an explicit canonical key has precedence. Chunk settings update atomically and affect subsequent attempts only. Runs/chunks retain strategy, settings, tokenizer/count mode, parser/representation, and effective chunker revision snapshots.

Startup idempotently migrates exact legacy hybrid-search equivalents into advanced-search settings, keeps explicit advanced values authoritative, and retires legacy hybrid keys.

## Deployment-managed connections

PostgreSQL and Neo4j connections, credentials, database/schema selection, and pool metadata are consumed before PostgreSQL overrides can safely load. Change them through environment variables, Compose/Kubernetes, or equivalent deployment configuration. The runtime settings UI must not imply that saving them can reconnect the running backend.

See [knowledge bases and profiles](../workflows/knowledge-bases-profiles.md) for provider compatibility and [chunking](../workflows/chunking-reprocessing.md) for migration after settings changes.
