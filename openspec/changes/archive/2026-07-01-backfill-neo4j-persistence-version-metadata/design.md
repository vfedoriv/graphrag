## Context

The generalized persistence hardening added SDN-managed version metadata to assigned-id Neo4j entities. That fixed warning-prone state detection for newly saved records, but existing records created before the mapping change may not have the required persistence version property.

The archived design listed this as a risk and assumed SDN would normalize existing records on subsequent saves. Runtime behavior showed a stricter path: updating an existing AI profile without the new `version` property can fail optimistic locking before the save can write the missing metadata.

## Goals / Non-Goals

**Goals:**

- Normalize missing persistence version metadata for existing assigned-id application nodes before normal update paths use SDN optimistic locking.
- Keep the backfill idempotent so already-versioned nodes are not modified on later starts.
- Preserve public API behavior and all business fields.
- Cover a legacy AI profile timeout update path with Neo4j-backed integration coverage.

**Non-Goals:**

- Changing request or response DTOs.
- Exposing persistence version metadata in APIs.
- Replacing SDN `@Version` mapping with custom state management.
- Changing business revision/version semantics such as AI profile `revision` or schema identity `version`.

## Decisions

1. Run a startup backfill for application labels that use SDN persistence version metadata.

   Rationale: the compatibility issue exists before the first update operation. Startup normalization makes existing deployments safe without requiring each service update path to know about legacy version metadata.

2. Preserve `SchemaDefinition.version` as business identity and backfill `entityVersion` separately.

   Rationale: schema `version` is part of immutable schema identity. The persistence metadata must remain distinct from that business field.

3. Keep the operation idempotent and value-preserving.

   Rationale: the backfill should only touch missing metadata and should not rewrite business fields, identifiers, labels, or relationships.

## Risks / Trade-offs

- [Risk] Startup now performs additional Neo4j writes on legacy deployments. -> Mitigation: the queries only match nodes missing metadata and become no-ops after the first successful startup.
- [Risk] A future assigned-id entity could be added without updating the backfill label list. -> Mitigation: persistence-state specs now make legacy metadata normalization part of the capability contract.
- [Risk] Backfill timing matters relative to startup services that save entities. -> Mitigation: run the backfill as a high-precedence application startup step.

## Migration Plan

1. Add a startup backfill that sets missing `version` metadata on assigned-id application nodes using the `version` persistence field.
2. Add a startup backfill that sets missing `entityVersion` metadata on existing `SchemaDefinition` nodes.
3. Add Neo4j-backed integration coverage for updating a legacy AI profile without version metadata.
4. Run focused AI profile, runtime settings, and knowledge-base integration tests.
5. Refresh the code graph after implementation.

Rollback is to remove the startup backfill and accept that deployments with legacy nodes may need a manual Neo4j metadata update before versioned saves can succeed.

## Open Questions

- None.
