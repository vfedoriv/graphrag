## Why

Knowledge-base lifecycle directly reads document state and invokes document graph cleanup, while AI compatibility mixes stored-chunk retrieval with rules and the AI persistence adapter reads knowledge-base assignments. Separating these dependencies completes roadmap step 3 and provides the prerequisite for document consolidation and schema registry/discovery boundaries.

## What Changes

- Add knowledge-base-owned ports for owned-document counts and knowledge-base artifact cleanup, implemented through document public capabilities and assembly adapters.
- Separate AI-owned deterministic embedding compatibility rules from document-owned stored embedding inspection, using immutable non-secret values.
- Add an AI-owned profile-assignment lookup port implemented through a knowledge-base capability; remove knowledge-base repository access from AI profile persistence.
- Preserve existing deletion ordering, compatibility rejection, tokenizer fallback, profile revision/assignment semantics, and store-specific transaction participation.
- Enforce the migrated dependency boundaries, including adapter and contract restrictions, with architecture tests.
- Align affected portal/contributor documentation and update the roadmap's completed-step references during implementation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: enforce knowledge-base lifecycle and AI profile/embedding boundaries through consumer-owned ports and provider public capabilities.

Existing `knowledge-base-lifecycle`, `ai-profile-management`, `embedding-space-management`, and graph cleanup behavior remains unchanged.

## Impact

Primary paths are `KnowledgeBaseService.delete` and `updateActiveAiProfile`, `AiProfileService.update` and `delete`, `EmbeddingSpacePolicy`, and `RelationalAiProfileRepository`. Document implementations retain chunk inspection and graph artifact cleanup; knowledge bases retain lifecycle and profile associations; AI retains profile management and compatibility decisions. Integration adapters belong under `bootstrap.integration` and translate immutable public values only.

This is step 3 of [the migration roadmap](../../../docs/MODULARIZATION_DESIGN.md), following archived [execution/recovery isolation](../archive/2026-10-01-isolate-reprocessing-execution-recovery/proposal.md) and [migration preparation isolation](../archive/2026-10-01-isolate-document-migration-preparation/proposal.md). Full document/schema/search relocation, provider-client redesign, new HTTP APIs, SQL migrations, persisted snapshot changes, query optimizations, separate build modules, and remote workers are outside this slice.
