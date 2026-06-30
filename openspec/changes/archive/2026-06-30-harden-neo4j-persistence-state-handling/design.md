## Context

The application persists multiple backend-owned domain nodes in Neo4j using assigned identifiers such as document ids, schema ids, knowledge-base ids, AI profile ids, chunk ids, and run ids. Spring Data Neo4j treats assigned-id entities without a `@Version` property as always new, which emits `DefaultNeo4jIsNewStrategy` warnings and bypasses the framework-supported state-detection path for business identifiers.

The runtime settings subsystem already established the local pattern for this problem: add a Spring Data `@Version` property to assigned-id entities and let SDN manage state metadata rather than implementing custom `Persistable` logic. The recent log cleanup applies the same principle to the remaining assigned-id application nodes and removes repository method signatures that trigger Spring Data projection metadata INFO logs for primitive `boolean` and `void` return types.

## Goals / Non-Goals

**Goals:**

- Ensure assigned-id Neo4j application entities use Spring Data Neo4j-supported state detection.
- Remove normal-path `DefaultNeo4jIsNewStrategy` warnings for assigned-id domain nodes.
- Avoid Spring Data projection metadata noise from custom repository methods returning primitive `boolean` or `void`.
- Preserve public API behavior, graph labels, relationships, business identifiers, and existing service orchestration semantics.
- Cover the change with focused unit tests and Neo4j-backed integration tests where repository mapping matters.

**Non-Goals:**

- Changing endpoint request or response DTOs.
- Replacing business identifiers with generated Neo4j internal ids.
- Introducing custom `Persistable` state management.
- Adding database uniqueness constraints, migrations, or new external dependencies.
- Reworking graph cleanup or extraction semantics beyond repository mapping hygiene.

## Decisions

1. Add Spring Data `@Version` metadata to assigned-id application nodes.

   Rationale: SDN documents `@Version` as the supported state-detection mechanism for entities with assigned ids. This follows the existing pattern used by `DocumentUploadNode` and `RuntimeSettingOverrideNode`.

   Alternative considered: implement `Persistable<String>` on each assigned-id entity. Rejected because it would spread manual new-state management across domain classes and repository load/save paths.

2. Keep version metadata internal to persistence models.

   Rationale: The version property is an implementation detail used by SDN. API DTOs and domain business semantics already expose separate fields such as schema `version` and AI profile `revision`; those should not be conflated with persistence metadata.

   Alternative considered: expose persistence versions in responses for debugging. Rejected because it would create an unnecessary API contract and could confuse users with domain-level version fields.

3. Use a distinct persistence version field where the entity already has a business version.

   Rationale: `SchemaDefinitionNode.version` is part of immutable schema identity. The SDN-managed field must not collide with that business meaning, so it should use a separate name such as `entityVersion`.

   Alternative considered: rename the schema business version. Rejected because that would be a public behavior and storage semantics change outside this proposal.

4. Avoid primitive `boolean` and `void` custom repository query returns in warning-sensitive paths.

   Rationale: Spring Data Commons can attempt projection metadata calculation for primitive and void return types, producing `PropertyDescriptorSource` INFO logs such as "Couldn't read class metadata for boolean/void." Wrapper Boolean returns and count-returning write queries avoid that metadata path while preserving service behavior.

   Alternative considered: suppress the Spring Data logger. Rejected because it hides all future metadata diagnostics rather than fixing local repository contracts that trigger noise.

## Risks / Trade-offs

- [Risk] Existing persisted nodes lack the new version property. -> Mitigation: SDN can load existing records and write version metadata on subsequent saves; tests should cover existing behavior at the service and integration level.
- [Risk] Adding version metadata to `SchemaDefinitionNode` could be confused with schema identity version. -> Mitigation: use a separate field name and keep API mapping unchanged.
- [Risk] Count-returning write queries could be accidentally used for business logic later. -> Mitigation: services should treat those returns as repository execution acknowledgements unless a future requirement explicitly needs counts.
- [Risk] Mock-only tests would miss SDN mapping warnings. -> Mitigation: run Neo4j-backed integration tests for document processing, schema, knowledge-base, and AI profile persistence paths.

## Migration Plan

1. Add SDN version metadata and getters to assigned-id Neo4j domain nodes that do not already have it.
2. Update custom repository existence methods to return nullable wrappers and service branches to use null-safe checks.
3. Update custom repository write/delete queries that return `void` to return row counts where needed.
4. Run focused unit tests for touched services.
5. Run Neo4j-backed integration tests and inspect logs for absence of `DefaultNeo4jIsNewStrategy` and `PropertyDescriptorSource` messages in the exercised paths.

Rollback is to remove the added version fields and restore primitive/void repository signatures, accepting the framework log noise again.

## Open Questions

- None.
