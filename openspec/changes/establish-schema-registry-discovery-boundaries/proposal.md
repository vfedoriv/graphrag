## Why

Schema registry/discovery still expose persistence-bearing schema contexts and directly access document and knowledge-base implementations. Roadmap step 5 establishes immutable schema contracts and owned input boundaries so document processing and later search/draft changes can consume schemas without foreign state access.

## What Changes

- Consolidate schema registry, parser/validator rules, active-schema resolution, and synchronous discovery under `schemas.registry` and `schemas.discovery`, with narrow public contracts.
- Return immutable complete schema snapshots carrying identity and content hash; preserve expected-target validation and missing-resource behavior.
- Move knowledge-base existence/provisioning and association access behind knowledge-base-owned capabilities and schemas-owned consumer ports, preserving relational transaction participation and activation semantics.
- Route discovery document reads and file parsing through documents public capabilities and schemas-owned input ports. Keep limits, source ordering/fingerprints, analysis chunking, guidance, aggregation, model-output handling, and review-only policy schema-owned.
- Migrate document extraction to the schema snapshot contract; retain narrow compatibility bridges for later search/draft/reprocessing organization where needed.
- Enforce registry/discovery and integration boundaries and remove the corresponding step-4 exceptions; align documentation during implementation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `active-schema-resolution`: Require immutable complete snapshots and content-hash-aware expected-target resolution without exporting persistence records.
- `architecture-boundary-governance`: Enforce schema registry/discovery ownership, document-input and knowledge-base-association boundaries, and scoped compatibility bridges.

## Impact

Touches `SchemaRegistryService`, `ActiveSchemaResolver`/context, schema parser/validator/model types, registry persistence adapters, `SchemaDiscoveryService`, `discovery` collaborators, related controller mapping, document extraction, and new bootstrap integration adapters. Knowledge-base association persistence ownership must be separated from schema definition persistence while preserving the existing tables and atomic activation behavior.

Step 3 is complete; step 4 is not a roadmap prerequisite. Implement after `consolidate-documents` in the agreed sequence, rebasing document capability mappings to its final packages. No HTTP, SQL, saved schema identity/hash, discovery result format, source persistence, or provider behavior change is intended. Durable drafts, evaluation/publication, general search migration, and final reprocessing consolidation remain steps 6–8/9.
