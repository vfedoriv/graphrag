## Why

Roadmap steps 1–6 have established document, registry/discovery, and draft-authoring ownership, but evaluation, publication, and the remaining reprocessing implementation still bypass those boundaries through explicitly frozen dependencies. Step 7 closes those seams so schema workflows can change independently without weakening held-out evaluation, publication recovery, or document-processing safety.

## What Changes

- Establish `schemas.evaluation` and `schemas.publication` ownership for their API mapping, workflows, deterministic rules, state, persistence, and recovery; complete `schemas.reprocessing` organization.
- Replace evaluation's document repository, storage, parser, chunker, extraction-client, and validator access with consumer-owned ports mapped to document public capabilities. Keep eligibility, run snapshots, claims, reuse, metrics, and advisory interpretation schema-owned.
- Expose document-owned preparation and dry extraction that cannot persist chunks, embeddings, processing/extraction runs, or graph facts. Return immutable raw and validated observations sufficient for existing metrics rather than document implementation types.
- Replace publication and reprocessing registry persistence access with immutable lookup, validation, and inactive-registration contracts. Preserve exact publication identity/content checks and explicit activation/reprocessing.
- Separate owner-specific checkpoints from the mixed draft checkpoint service and replace downstream persistence reads with bounded summary interfaces without introducing list-response N+1 queries.
- Retire the exact step-7 document/registry and downstream ownership exceptions; retain only explicitly identified step-8 search and step-9 support/assembly exceptions.
- Align architecture documentation and contributor guidance during implementation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: Enforce evaluation/publication/reprocessing ownership, public document dry-extraction and registry boundaries, owner-specific checkpoints, bounded navigation summaries, and exact step-7 exception retirement.

## Impact

Affected areas include legacy evaluation/publication/reprocessing controllers, DTOs, services, domain records, repository ports and relational adapters; `schemas.drafts` checkpoint/navigation seams; `schemas.registry` contracts; document inspection/extraction capabilities; mapping adapters under `bootstrap.integration`; persistence scanning and executor/recovery wiring; architecture and workflow tests; and the portal, migration roadmap, README, AGENTS, and CLAUDE guidance.

This is a boundary migration within the existing application. HTTP paths and JSON/error contracts, SQL/table mappings, serialized histories and snapshot fingerprints, binary storage, processing algorithms, metric formulas, retry/recovery semantics, profile behavior, and typed settings remain compatible. No new library, build module, remote worker, database migration, or deployment service is planned. Search consolidation and general support/assembly reorganization remain steps 8 and 9.
