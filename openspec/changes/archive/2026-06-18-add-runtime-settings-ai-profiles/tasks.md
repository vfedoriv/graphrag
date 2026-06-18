## 1. Runtime Settings Foundation

- [x] 1.1 Add Neo4j domain/repository support for persisted runtime setting overrides.
- [x] 1.2 Add a runtime settings registry that defines allowlisted keys, categories, value types, defaults, mutability, live-apply status, sensitivity, and constraints.
- [x] 1.3 Add validation and typed accessors for query, hybrid search, extraction, chunking, and AI observability settings.
- [x] 1.4 Refactor services that require live settings to read through the runtime settings accessors instead of immutable startup property records.
- [x] 1.5 Add runtime settings REST endpoints for listing, updating, and clearing allowlisted setting overrides.

## 2. AI Profile Persistence And Seeding

- [x] 2.1 Add Neo4j domain/repository support for OpenAI-compatible AI provider profiles.
- [x] 2.2 Add profile validation for name, base URL, chat model, embedding model, embedding dimensions, timeout, retry, and secret update semantics.
- [x] 2.3 Implement write-only API key handling so read responses expose only masked/configured metadata.
- [x] 2.4 Add startup seeding for the default AI profile from existing `app.model.*` configuration when no default profile exists.
- [x] 2.5 Add AI profile REST endpoints for create, list, get, update, and delete.

## 3. Knowledge-Base Profile Activation

- [x] 3.1 Extend knowledge base persistence and responses with active AI profile identity.
- [x] 3.2 Assign the default AI profile to new knowledge bases.
- [x] 3.3 Add endpoints to read and update a knowledge base active AI profile.
- [x] 3.4 Implement embedding compatibility checks against processed document chunk embeddings and stored embedding metadata.
- [x] 3.5 Reject incompatible profile assignment without changing the previous active profile.

## 4. Profile-Aware AI Runtime

- [x] 4.1 Add profile-aware chat and embedding model factory components using Spring AI OpenAI runtime options.
- [x] 4.2 Cache runtime clients by profile id and profile revision, and invalidate cache entries when profiles change.
- [x] 4.3 Route document processing, graph extraction, Cypher generation, ask, and hybrid search through knowledge-base active profile resolution.
- [x] 4.4 Preserve deterministic test-client injection paths so existing AI workflow tests do not require network calls.
- [x] 4.5 Return clear configuration errors when a required active profile or model role cannot be resolved.

## 5. Schema Generation And Observability

- [x] 5.1 Add knowledge-base-scoped schema generation endpoints for text and file inputs.
- [x] 5.2 Add knowledge-base-scoped schema example generation endpoints for text and file inputs.
- [x] 5.3 Keep existing global schema generation endpoints compatible and backed by the default profile behavior.
- [x] 5.4 Update AI observability to read runtime privacy/tag settings per call.
- [x] 5.5 Include active profile provider/model metadata in observations when configured.

## 6. Tests And Documentation

- [x] 6.1 Add unit tests for runtime settings validation, fallback, persistence, and live accessor behavior.
- [x] 6.2 Add unit and controller tests for AI profile CRUD, API key masking, and secret update semantics.
- [x] 6.3 Add integration tests for profile seeding, new knowledge-base default profile assignment, and persisted active profile selection.
- [x] 6.4 Add service/integration tests proving query, hybrid search, extraction, chunking, schema generation, and observability use updated runtime settings or active profiles.
- [x] 6.5 Add tests for embedding compatibility rejection and unchanged previous profile on failure.
- [x] 6.6 Update `README.md`, `AGENTS.md`, and `CLAUDE.md` where overlapping configuration, AI profile, secret masking, and live-apply facts are documented.
- [x] 6.7 Run `./mvnw test`.
