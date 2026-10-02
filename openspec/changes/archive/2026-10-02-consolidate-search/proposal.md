## Why

Roadmap steps 1–7 established document and schema ownership, but query/ask and advanced search remain spread across legacy packages and directly consume foreign persistence and schema implementations. Step 8 establishes search ownership and retires its exact transitional dependencies before final support and assembly cleanup.

## What Changes

- Consolidate query/ask and advanced-search API mapping, workflows, deterministic policy, planning, retrieval, ranking, answering, durable runs, and owned persistence under `search`.
- Place query execution, planner inspection, graph/text retrieval, and parent-context reads behind search-owned ports and adapters using shared database connections.
- Replace foreign document metadata repositories with scoped, bounded public capabilities, including batch citation metadata lookup and metadata-filter selection.
- Replace schema and knowledge-base repository/implementation access with immutable public facts and search-owned consumer ports mapped by transaction-free bootstrap integration adapters.
- Use AI-owned embedding compatibility for readiness and dense retrieval; retire `EmbeddingSpacePolicy` after its final search callers migrate.
- Preserve readiness/admission ordering, caller transactions, stored snapshots, query guardrails, citation provenance, cancellation, recovery, and branch-local failure behavior.
- Enforce search ownership and retire exact step-8 exceptions while freezing identified remaining support/assembly seams for step 9.
- Align the architecture portal, roadmap, and contributor guidance during implementation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: Enforce search ownership, foreign public-contract access, bounded document metadata capabilities, search-owned retrieval/query adapters, and exact step-8 exception retirement.

## Impact

Affected areas include legacy query and advanced-search controllers/DTOs/services/domain values, query policy and execution infrastructure, retrieval repository ports and Neo4j adapters, advanced-search relational records/adapters, document metadata capabilities, schema and knowledge-base public facts, AI compatibility integration, bootstrap wiring, architecture and behavioral tests, and documentation.

This is an ownership migration within the existing application. HTTP paths, JSON/error contracts, SQL mappings, persisted run/result/settings/schema snapshots, payload versions, provider behavior, runtime policy, retrieval/ranking/answer algorithms, and operational recovery remain compatible. No SQL migration, new dependency, separate build module, remote worker, or deployment service is planned. General AI/settings/support relocation, shared index-maintenance ownership, and final assembly cleanup remain step 9.
