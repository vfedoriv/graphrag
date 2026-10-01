## Why

Roadmap steps 1–3 established document capabilities, but their implementations still depend on document services, stages, records, and adapters scattered across broad packages. Consolidating those responsibilities now makes document ownership enforceable and supplies a stable foundation for schema and search migration.

## What Changes

- Consolidate document management, processing, extraction, run history/recovery, storage mutation/reconciliation, parsing/chunking, and owned persistence/graph adapters under `documents`.
- Retain the public document capabilities established in steps 1–3; keep schema plan policy and knowledge-base lifecycle admission with their current owners.
- Separate deterministic document logic, application orchestration, repository ports, and external-effect adapters without introducing empty layers.
- Replace document callers of `EmbeddingSpacePolicy` with the existing AI-owned compatibility capability and immutable target values.
- Enforce document internals and pure-rule boundaries, freezing existing foreign callers and support dependencies by exact class/dependency until their assigned roadmap slices.
- Preserve HTTP behavior, processing algorithms, historical snapshots, checkpoint sequencing, and recovery behavior; align documentation during implementation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: Enforce consolidated document ownership, deterministic logic isolation, public capability access, and bounded transitional exceptions.

## Impact

Touches document controllers/DTO mapping, broad `service`, `application.processing`, `document`, graph-write/cleanup, document repository/model types, and document-owned adapters currently under shared persistence infrastructure. Shared connections, transaction annotations, AI provider infrastructure, and schema-draft binary storage remain with their existing support/feature owners. Existing imports and deterministic/integration tests need migration; HTTP routes, SQL/Flyway schema, binary locations, and serialized snapshots do not change.

Step 3 is complete. Step 5 is independently prerequisite-ready, but the agreed execution order is this change followed by `establish-schema-registry-discovery-boundaries`; existing schema resolver access is frozen here and retired there. Drafts, evaluation/publication, and search migrate in steps 6–8.
