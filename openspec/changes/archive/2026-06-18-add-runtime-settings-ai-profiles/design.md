## Context

The backend currently binds `app.*`, `spring.ai.*`, and management properties at startup. Services inject immutable `@ConfigurationProperties` records, so changing the Spring `Environment` would not automatically update running services. Spring Boot Actuator can expose `env` and `configprops` for diagnostics, but the built-in environment endpoint is read-only in current Spring Boot and would be too broad as a UI write surface.

The application already persists knowledge bases, documents, schemas, chunks, and extraction state in Neo4j. AI calls flow through Spring AI chat and embedding abstractions, with OpenAI-compatible configuration coming from startup properties. The Spring AI OpenAI artifacts already on the classpath expose builders/options for base URL, API key, chat model, embedding model, embedding dimensions, timeout, and retries, so the application can own runtime model construction for OpenAI-compatible profiles.

## Goals / Non-Goals

**Goals:**
- Expose a safe backend API for runtime-editable settings needed by the UI.
- Persist settings and AI provider profiles in Neo4j.
- Apply allowlisted safe settings without restart.
- Let each knowledge base choose an active OpenAI-compatible AI profile.
- Route KB-scoped AI workflows through the selected profile.
- Protect API keys from read responses and logs.
- Preserve existing global schema generation endpoints while adding KB-scoped alternatives.

**Non-Goals:**
- Expose arbitrary Spring or system properties for mutation.
- Add frontend changes.
- Add authentication or authorization.
- Encrypt API keys at rest in v1.
- Automatically re-embed or reprocess existing documents when embedding dimensions change.
- Support non-OpenAI-compatible providers in v1.
- Dynamically reconfigure Neo4j, storage root, multipart limits, management endpoint exposure, or OTLP exporter infrastructure.

## Decisions

1. Store runtime settings and AI profiles as Neo4j application data.
   - Rationale: the UI needs to list, edit, and select settings through normal backend APIs, and the project already owns Neo4j persistence.
   - Alternative considered: write `.properties` files. Rejected because it is brittle for packaged deployments and still does not live-apply injected records.
   - Alternative considered: use Actuator `env`. Rejected because it is read-only in current Spring Boot and too broad for safe mutation.

2. Add a narrow runtime settings registry instead of mutating `AppProperties`.
   - Rationale: existing records are good startup defaults, but runtime behavior needs a mutable source that services read at use time.
   - Implementation shape: introduce a settings service that exposes typed accessors for allowlisted categories and falls back to startup defaults when no persisted override exists.
   - Services that need live settings depend on this accessor, not directly on immutable `AppProperties`.

3. Model AI provider configuration as profile pairs.
   - Rationale: a coherent profile keeps base URL/API key/chat model/embedding model/dimensions together and avoids invalid UI combinations.
   - Each profile is OpenAI-compatible and has one chat role plus one embedding role.
   - Separate chat-only and embedding-only profile catalogs are deferred until a real need appears.

4. Apply active AI profiles per knowledge base.
   - Rationale: the user explicitly wants profile selection to be per knowledge base.
   - KB-scoped workflows resolve the active profile by `knowledgeBaseId`.
   - New knowledge bases inherit a seeded default profile unless explicitly assigned.
   - Non-KB legacy schema generation endpoints keep existing behavior and use the default profile.

5. Build runtime Spring AI clients from the active profile.
   - Rationale: Spring Boot property rebinding will not update existing model beans, and the runtime profile catalog lives in Neo4j.
   - Use Spring AI OpenAI model builders/options to construct or cache clients by profile revision.
   - In-flight calls keep the model/profile snapshot resolved at call start.

6. Block incompatible embedding profile activation.
   - Rationale: stored chunk embeddings and the Neo4j vector index depend on embedding dimensionality. Allowing incompatible profile changes can make hybrid search fail or return invalid results.
   - For v1, reject activation when the KB already has processed embeddings and the new profile’s embedding model or dimensions differ from the embedding metadata associated with that KB.
   - Re-embedding is left to a future explicit workflow.

7. Treat secrets as write-only API fields.
   - Rationale: API keys are needed for runtime profiles, but returning them would unnecessarily broaden exposure.
   - Read responses expose `apiKeyConfigured` and masked metadata only.
   - Update requests may omit the key to retain it, send a new key to replace it, or use an explicit clear operation if clearing is supported.

8. Keep observability runtime-controlled but infrastructure-startup-bound.
   - Rationale: privacy and tag controls are safe to apply at runtime because application code reads them per call. OTLP exporter endpoint and tracing infrastructure remain startup properties.
   - Model/provider tags use active profile metadata when enabled.

## Risks / Trade-offs

- [Risk] Plaintext API keys in Neo4j are acceptable for local/trusted deployments but weak for shared environments. → Mitigation: never return or log key values, document the limitation, and leave encrypted storage as a future enhancement.
- [Risk] Runtime client creation can add latency or leak resources if done per call. → Mitigation: cache clients by profile id and revision, and invalidate on profile update.
- [Risk] Per-KB profiles complicate schema generation because existing endpoints are global. → Mitigation: add KB-scoped endpoints and keep legacy global endpoints on the default profile.
- [Risk] Existing tests may mock `ChatModel`/`EmbeddingModel` beans directly. → Mitigation: introduce profile-aware client factories behind local interfaces so tests can keep deterministic clients without network calls.
- [Risk] Runtime settings could become an unbounded property editor. → Mitigation: implement an explicit allowlist with typed validation and metadata.

## Migration Plan

1. Add Neo4j domain/repository/service support for runtime settings and AI profiles.
2. On startup, seed missing default runtime settings from current properties and seed an initial OpenAI-compatible AI profile from `app.model.*`.
3. Assign the default profile to newly created knowledge bases.
4. For existing knowledge bases without an active profile, resolve the default profile until they are explicitly assigned or backfilled.
5. Route AI workflows through the profile-aware runtime factory.
6. Update docs to explain runtime settings, profile seeding, secret masking, and embedding compatibility.

Rollback is to disable or remove the new endpoints and route services back to startup-bound Spring AI beans. Persisted runtime setting/profile nodes can remain unused without affecting the existing graph domain.
