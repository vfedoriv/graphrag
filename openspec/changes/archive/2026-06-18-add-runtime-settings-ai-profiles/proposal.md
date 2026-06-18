## Why

Application behavior is currently controlled by startup properties, which makes operational tuning and AI provider selection unavailable to the existing UI without changing files and restarting the backend. The backend needs a safe, allowlisted configuration API plus persisted AI provider profiles so users can manage model endpoints and apply knowledge-base-specific AI settings from the UI.

## What Changes

- Add a backend-owned runtime settings API for allowlisted application settings, with validation, metadata, persistence in Neo4j, and live application for settings that are safe to change without rebuilding infrastructure.
- Add a Neo4j-backed AI provider profile catalog for OpenAI-compatible providers, including base URL, write-only API key, chat model, embedding model, embedding dimensions, timeout, and retry metadata.
- Seed an initial AI provider profile from existing `app.model.*` startup properties and use it as the default for new knowledge bases.
- Allow each knowledge base to select an active AI provider profile.
- Resolve chat and embedding clients from the active knowledge-base profile for document processing, graph extraction, Cypher generation, ask, and hybrid search workflows.
- Add knowledge-base-scoped schema generation endpoints that use the knowledge base active AI profile.
- Keep existing global schema generation endpoints for compatibility.
- Reject profile activation when the target embedding settings are incompatible with already processed document embeddings in that knowledge base.
- Mask API keys in all read responses and logs while allowing write-only key updates.

## Capabilities

### New Capabilities
- `runtime-application-settings`: Exposes allowlisted mutable application settings through a validated REST API and applies safe settings live.
- `ai-profile-management`: Manages persisted OpenAI-compatible AI provider profiles and knowledge-base-specific active profile selection.

### Modified Capabilities
- `hybrid-search`: Hybrid search resolves embeddings from the requested knowledge base active AI profile and enforces compatibility with stored embeddings.
- `schema-generation-with-examples`: Adds knowledge-base-scoped schema and example generation endpoints that use the knowledge base active AI profile.
- `ai-observability-monitoring`: AI observability uses runtime settings and active profile metadata when reporting model/provider tags and content capture controls.
- `documentation-alignment`: Repository documentation must describe runtime settings, AI profiles, secret masking, and restart/live-apply behavior.

## Impact

- Affected APIs: new settings endpoints, new AI profile endpoints, knowledge-base profile assignment endpoints, and knowledge-base-scoped schema generation endpoints.
- Affected services: configuration access, AI client resolution, document processing, graph extraction, Cypher generation, ask orchestration, hybrid search, schema generation, observability.
- Affected persistence: new Neo4j nodes or properties for runtime settings, AI profiles, and knowledge-base active profile references.
- Affected tests: controller/API tests, service tests for runtime application and profile resolution, Neo4j integration tests for persistence and seeding, compatibility tests for embedding dimensions, and existing workflow regression tests.
- Dependencies: no new external service is required; Spring AI OpenAI classes already present in the project are used for runtime OpenAI-compatible clients.
