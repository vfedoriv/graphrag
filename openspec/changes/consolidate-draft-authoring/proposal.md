## Why

Roadmap step 5 established registry, discovery, document-input, and knowledge-base association boundaries, but durable draft authoring still reaches their implementation services, repositories, and records directly. Consolidating authoring now gives the later evaluation/publication slice a stable schema-owned boundary without changing draft behavior or persisted history.

## What Changes

- Group draft lifecycle, sources and storage recovery, durable analysis, review, conflicts, and their owned relational persistence under `schemas.drafts`.
- Route draft access to documents, registry, knowledge-base state, and AI profile context through purpose-specific contracts and mapping-only integration adapters. Reuse established capabilities where they express the required facts; extend them narrowly where draft snapshot or batch needs are missing.
- Keep source revision/fingerprint checks, analysis claims and deadlines, deterministic aggregation, review decisions, and historical navigation equivalent while relocating their owners.
- Enforce the authoring boundary and retire the exact step-6 exceptions in `ArchitectureBoundaryTest`. Retain named step-7 evaluation/publication and reprocessing, step-8 search, and step-9 support exceptions.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: Require schema draft authoring to use owned persistence and public feature contracts, with only exact later-roadmap dependencies remaining transitional.

Existing `schema-draft-lifecycle`, `schema-draft-sources`, `schema-draft-analysis`, `schema-draft-review`, and application checkpoint requirements remain behaviorally unchanged.

## Impact

Touches draft controllers/API mapping, authoring services and values, draft/source/analysis/review repositories and adapters, documents and registry/knowledge-base public capabilities, bootstrap integration, architecture tests, and matching portal/contributor documentation. HTTP paths, response and error contracts, SQL tables, binary locations, stored snapshots, model behavior, and workflow semantics remain unchanged. Evaluation/publication, final reprocessing organization, and search consolidation remain later roadmap slices.
