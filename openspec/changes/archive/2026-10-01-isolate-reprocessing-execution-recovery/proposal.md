## Why

Schema reprocessing execution and recovery directly depend on document records,
repositories, and processing internals. Establishing an explicit documents
boundary makes these workflows independently testable and prevents a future
schemas/documents source dependency cycle.

## What Changes

- Add schemas-owned ports for revision-aware document execution and completed
  processing-outcome inspection, implemented by application integration adapters
  calling a documents-owned public capability.
- Move source validation and document-specific execution-input reconstruction
  behind that capability, retaining current outcomes and exception translation.
- Migrate both schema-activation and chunk-migration execution and recovery.
- Enforce the migrated boundary with architecture tests. Freeze preparation-only
  dependencies explicitly for the following change.
- Update the architecture portal, codebase tour, and overlapping contributor facts
  to describe the implemented slice.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: enforce document ownership for reprocessing
  execution and recovery, with consumer-owned ports and integration adapters.

Public reprocessing, chunk migration, processing, and recovery behavior remains
governed by its existing specifications; no HTTP or persisted-format change is
proposed.

## Impact

Primary callers are `SchemaReprocessingPlanService.processItem` and
`SchemaReprocessingRecoveryService.completedOverwrite`. The implementation adds
documents contracts/facades, schemas ports, and bootstrap integration adapters;
it changes focused tests and architecture governance, not database migrations,
provider configuration, processing algorithms, or deployments.

This is roadmap change 1 in [the high-level design](../../../../docs/MODULARIZATION_DESIGN.md).
It has no predecessor. Change 2 depends on its contracts and completes migration
preparation isolation. Wholesale feature relocation, AI/knowledge-base boundaries,
remote workers, reusable build modules, and broad service decomposition are out of
scope.
