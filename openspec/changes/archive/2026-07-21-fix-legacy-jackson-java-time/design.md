## Context

Spring Boot 4.1 uses Jackson 3 for its preferred HTTP JSON mapping, while internal components that depend on `com.fasterxml.jackson` use a separately declared Jackson 2 `ObjectMapper`. The legacy mapper is currently created with `new ObjectMapper()` and therefore does not register the already-present JSR-310 module. Evaluation creation canonicalizes `DecisionResponse` values before saving the run; a non-empty history contains `Instant createdAt` and fails at that serialization boundary.

The evaluation contract already requires decision snapshots. The correction must preserve deterministic canonical JSON, avoid changing the public API, and avoid hiding unsupported Java-time handling through permissive mapper features.

## Goals / Non-Goals

**Goals:**

- Make Java-time values supported by the shared legacy Jackson 2 mapper.
- Use an explicit ISO-8601 string representation for legacy-mapper date/time serialization.
- Allow a valid evaluation with non-empty decision history to reach durable run persistence.
- Verify both canonical serialization and the persisted evaluation snapshot.

**Non-Goals:**

- Migrating internal components from Jackson 2 to Jackson 3.
- Changing Spring MVC's Jackson 3 mapper or public response contracts.
- Adding a Jackson dependency that is already supplied transitively.
- Changing evaluation eligibility, decision semantics, or frontend error presentation.

## Decisions

### Configure Java-time support at the shared legacy mapper boundary

`LegacyJacksonConfiguration` will register Jackson 2's `JavaTimeModule` on the shared mapper. This fixes the actual configuration boundary and applies consistent Java-time support to injected legacy-mapper consumers.

Alternative considered: register the module only inside `SchemaDraftJsonSupport`. This would make one consumer work while leaving the shared mapper incorrectly configured and could produce inconsistent internal JSON.

Alternative considered: disable `REQUIRE_HANDLERS_FOR_JAVA8_TIMES`. This suppresses the useful configuration failure without installing the serializers required for an intentional representation.

### Persist Java-time values as ISO-8601 strings

The legacy mapper will disable `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`. Decision timestamps in canonical snapshots will therefore be readable ISO-8601 strings rather than numeric epoch values. The choice is explicit so the canonical fingerprint input does not depend on Jackson 2 defaults or module-discovery behavior.

Alternative considered: register `JavaTimeModule` while retaining numeric timestamps. Numeric output is deterministic, but it is harder to inspect and differs from the application's HTTP timestamp representation.

Alternative considered: call `findAndRegisterModules()`. Classpath discovery would currently find JSR-310 support, but explicit registration better documents the required module and avoids unrelated module discovery changing canonical internal JSON.

### Cover the configuration and durable workflow separately

A focused test will obtain the mapper configuration used by the application and canonicalize a non-empty `List<DecisionResponse>` with a fixed `Instant`, asserting the string representation. An integration test will record a decision, start an otherwise valid held-out evaluation, and verify the saved run's `decisionsJson` and timestamp. Together these tests identify mapper regressions precisely while proving the user-visible workflow.

## Risks / Trade-offs

- [The shared mapper's date serialization changes for every injected Jackson 2 consumer] → Keep the setting explicit, inspect affected tests, and run the full suite; ISO-8601 strings are the intended common representation.
- [An integration assertion could become timing-sensitive if it compares generated timestamps] → Assert the persisted snapshot timestamp against the already returned or repository-stored decision timestamp rather than wall-clock expectations.
- [A test-local configured mapper could pass while production configuration remains broken] → Construct or inject the mapper from `LegacyJacksonConfiguration` in focused regression coverage.

## Migration Plan

Deploy the mapper configuration and regression tests together. No dependency, schema, or stored-data migration is required. Existing evaluation snapshots remain readable because decision snapshots are parsed as JSON trees rather than deserialized into a timestamp-specific persistence type. Rollback consists of reverting the mapper configuration and tests, though doing so reintroduces the failure for non-empty decision history.

## Open Questions

None.
