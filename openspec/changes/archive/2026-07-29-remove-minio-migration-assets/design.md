## Context

The optional Langfuse profile now uses Garage for event and media object storage, and its initialization, authorization, browser-reachable media URLs, persistence, and object operations have passed acceptance checks. The repository still carries a transitional object-store migration overlay, rollback overlay, copy/verification script, runbook, contributor warnings, main-spec requirements, and test assertions. Those assets describe an operational path the project no longer intends to support.

Archived OpenSpec changes are the historical decision record and must remain unchanged. The current Garage services, volumes, credentials, smoke test, and GraphRAG local filesystem binary storage are unrelated to the cleanup.

## Goals / Non-Goals

**Goals:**

- Make Garage the only object store named by active runtime, operational, contributor, test, and product-contract content.
- Delete the transitional migration and rollback overlays, script, and runbook.
- Remove transition-specific guidance from `README.md`, `AGENTS.md`, and `CLAUDE.md`.
- Remove the completed migration/rollback requirement from the main capability spec and express the surviving local object-storage requirement entirely in Garage terms.
- Replace transition-specific tests with positive Garage-only configuration coverage.
- Preserve archived OpenSpec artifacts as historical records.

**Non-Goals:**

- Delete or mutate local Docker containers, volumes, Garage buckets, or stored objects.
- Change the Garage image, layout, credentials, endpoints, initialization, or smoke-test behavior.
- Change Langfuse, GraphRAG APIs, observability behavior, or application binary storage.
- Provide a new object-store migration or rollback mechanism.

## Decisions

### Delete transitional assets instead of deprecating them in place

The migration Compose overlay, rollback overlay, migration script, and runbook will be removed. Keeping deprecated files would continue to advertise an unsupported path and would prevent the repository from reaching a Garage-only state.

Alternative considered: retain the files under a legacy or archive directory. Rejected because OpenSpec already preserves the historical implementation decision, while executable legacy assets can be mistaken for supported operations.

### Remove transition language from active contracts and guidance

The `ai-observability-monitoring` migration requirement will be removed when the delta spec is synced. The surviving Garage requirement will be rewritten without references to the former provider. README and contributor guidance will describe only Garage startup, persistence, smoke testing, and backup expectations.

Alternative considered: keep negative “must not use the former provider” language. Rejected because the desired end state is a positive Garage-only contract with no active legacy-provider vocabulary.

### Verify Garage positively and enforce a zero-reference boundary

Compose tests will continue to assert the pinned Garage service, initializer dependency, persistent volumes, endpoints, credentials, and smoke-test structure through the resolved Garage configuration rather than hard-coded legacy names. Verification will also search active product, operational, contributor, and test content for legacy-provider references while excluding OpenSpec change records and generated graph output, which intentionally retain historical evidence.

Alternative considered: retain string-based negative assertions. Rejected because those assertions themselves preserve the obsolete name and are weaker than complete positive Garage configuration checks plus a repository-wide reference audit.

### Preserve historical OpenSpec archives unchanged

Archived changes document why the migration existed, how it was verified, and why it was later retired. They remain the audit trail and are explicitly excluded from the active-reference cleanup.

Alternative considered: rewrite or delete archived artifacts. Rejected because that would erase historical product decisions and violate the repository’s OpenSpec workflow.

## Risks / Trade-offs

- [Risk] An operator still expects repository-provided rollback tooling → Mark the removal as breaking and retain the prior workflow in the historical OpenSpec archive.
- [Risk] Broad text cleanup accidentally edits planning or archived history → Scope reference checks and edits to active product, operational, contributor, test, and main-spec files while excluding `openspec/changes` and generated graph output.
- [Risk] Removing negative assertions weakens regression coverage → Expand positive assertions for every required Garage service, dependency, endpoint, credential, volume, and smoke-test asset.
- [Risk] Existing local Docker resources remain after repository cleanup → Document that runtime resource deletion is outside this change and has no effect on the Garage-backed profile.
- [Trade-off] The repository will no longer provide a supported reverse cutover path → This is intentional because the user has accepted Garage as the sole local object store.

## Migration Plan

1. Remove the migration and rollback Compose overlays, migration script, and migration runbook.
2. Remove transition and rollback guidance from README and contributor instructions while retaining Garage backup and persistence guidance.
3. Update Compose configuration tests to cover only active Garage behavior.
4. Apply the delta spec to remove the transitional requirement and rewrite the Garage requirement.
5. Verify resolved default and Langfuse Compose profiles, run the Garage configuration test and smoke test, audit active files for legacy references, and update graphify output.

Code-level rollback is a normal Git revert that restores the deleted assets and prior contract. This change does not mutate object-store data, so no data rollback is required.

## Open Questions

None.
