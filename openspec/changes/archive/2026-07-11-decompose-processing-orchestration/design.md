## Context

`DocumentProcessingService` currently owns option resolution, storage reads, parsing, chunk creation, embedding client selection, vector-index creation, chunk persistence, run lifecycle, graph extraction, observations, and error handling. Runtime settings and schema generation similarly combine catalog data, serialization, provider calls, and presentation concerns. The technical-layer package layout has no explicit application-workflow boundary.

## Goals / Non-Goals

**Goals:**
- Reduce orchestration components to clear coordination responsibilities with explicit stage contracts.
- Move deterministic mapping, parsing, and policy work into side-effect-free typed collaborators.
- Centralize AI/embedding client resolution and runtime-setting composition.
- Preserve public APIs, persisted run semantics, observability, and failure behavior during incremental extraction.

**Non-Goals:**
- Change document processing from synchronous to asynchronous.
- Redesign graph provenance, embedding-space policy, or query guardrails defined by their dedicated changes.
- Perform a repository-wide package rewrite or introduce a new framework.

## Decisions

### Keep compatibility facades and extract stages incrementally

Existing public services remain compatibility facades while explicit workflow coordinators own document processing, schema generation, and settings composition. Document processing is decomposed into option resolution, source parsing, chunk preparation, embedding/persistence, graph extraction, and run lifecycle stages. This avoids a risky flag-day API or controller rewrite.

### Separate pure policy from adapters

Typed settings catalog/codecs, chunk metadata mapping, schema graph mapping, prompt construction, and model-response extraction are pure collaborators with direct unit tests. Neo4j, filesystem, model client, and logging calls remain in named adapters or workflow stages.

### Make ownership visible in architecture tests

Architecture rules use explicit application-workflow and infrastructure-adapter packages. Direct `Neo4jClient` access is allowed only in named persistence adapters, replacing a growing class-level exception list. Transitional exceptions remain explicit until migrated.

For this change, database-boundary migration is scoped to document processing, runtime settings, and schema generation. Existing direct `Neo4jClient` users in unrelated query, graph-writing, migration, cleanup, knowledge-base, and schema-registry features remain frozen legacy exceptions; migrating those features requires separate changes rather than expanding this change into a repository-wide rewrite.

### Use injected shared serialization and reusable client resolution

Use the configured application `ObjectMapper` rather than ad hoc instances. One resolver selects profile-scoped embedding and chat clients; embedding compatibility itself remains owned by the embedding-space change.

### Persist recoverable processing-stage checkpoints

Processing-run creation, stage transitions, completion, and failure updates are recoverable persisted checkpoints. Each lifecycle update commits independently from later parsing, chunking, embedding, persistence, or graph-extraction work so the latest stage and failure details survive a downstream rollback. A failed replacement run does not deactivate a previously active completed run; only successful completion activates the new run and deactivates older completed runs.

## Risks / Trade-offs

- [Temporary facade delegation adds classes] → Keep public facades thin and delete them only in a later API version change if needed.
- [Stage extraction changes transaction boundaries] → Define stage ownership first and preserve run status/persistence ordering with integration tests.
- [Architecture rules may block incremental work] → Add narrow transitional exceptions with expiry tasks, never broad package exemptions.
- [Refactor obscures behavior regressions] → Preserve golden integration tests before moving each stage and add focused unit tests first.

## Migration Plan

1. Add pure collaborators and characterize current behavior with focused tests.
2. Extract document-processing stages behind the existing service facade one stage at a time.
3. Extract settings and schema-generation collaborators, then replace duplicated resolver code.
4. Move direct database operations to named adapters and tighten architecture rules after each migration.

## Open Questions

- Is a separate application package preferable to feature-local workflow packages after the first extraction proves the boundary?
