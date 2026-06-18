## Context

`RuntimeSettingsService` currently exposes an allowlisted subset of settings from `AppProperties` and `AiObservabilityProperties`, persists overrides as `RuntimeSettingOverride` nodes, and resolves those overrides through typed accessors used by query, chunking, extraction, and AI observability workflows. That model is safe because values are validated before persistence and callers do not read arbitrary strings from the environment.

`src/main/resources/application.properties` has more operational configuration than the current allowlist: Spring application identity, logging, Spring AI bootstrap switches, Neo4j connection/database settings, AI model seeding defaults, storage paths, multipart limits, actuator/health/tracing/exporter settings, and Spring auto-configuration controls. Some are suitable for live update, some are useful only for visibility, and some are sensitive.

The frontend management app needs one catalog that can render all relevant backend-owned configuration with clear editability and restart semantics. It should not imply that every Spring Boot property can be mutated after startup.

## Goals / Non-Goals

**Goals:**

- Expand the runtime settings list so external clients can inspect the relevant `application.properties` groups and profile-resolved startup defaults.
- Keep mutable settings backend-owned, allowlisted, typed, constrained, and persisted.
- Mark startup-bound settings as read-only unless the implementation can safely apply a change to subsequent work.
- Mask secret values in read responses while still indicating whether a value is configured.
- Provide frontend-friendly metadata such as category, value type, source, mutability, live-apply status, restart requirement, sensitivity, constraints, and a read-only/update reason.
- Preserve the current typed accessor pattern for feature services.

**Non-Goals:**

- General-purpose editing of arbitrary Spring `Environment` keys.
- Runtime reconfiguration of Neo4j connections, actuator endpoint exposure, multipart resolver limits, Spring auto-configuration, or OpenTelemetry exporter infrastructure.
- Replacing AI profile management with raw `app.model.*` or `spring.ai.openai.*` setting edits.
- Persisting changes back into `application.properties`; runtime overrides remain application data in Neo4j.

## Decisions

1. Keep an explicit settings catalog instead of exposing all environment properties.

   Rationale: arbitrary environment editing would expose framework internals and secrets, and most Spring Boot infrastructure binds at startup. The catalog can include read-only entries for visibility without allowing unsafe writes.

   Alternative considered: introspect every property under `spring.*`, `management.*`, and `app.*`. Rejected because it would leak sensitive values and create an unstable external contract around implementation details.

2. Split settings into update modes.

   The catalog should distinguish:

   - `LIVE`: mutable and applied to subsequent workflow executions through existing typed accessors.
   - `RESTART_REQUIRED`: known and visible, but not mutable through the runtime API in this change.
   - `PROFILE_MANAGED`: visible as startup/default context, but operational changes belong to AI profile endpoints.
   - `READ_ONLY`: informational settings that should not be updated externally.
   - `SENSITIVE_READ_ONLY`: secret or credential settings that must be masked.

   Rationale: `mutable` and `liveApplied` are not enough for frontend UX. Clients need to know whether a disabled edit control means "restart required", "managed elsewhere", or "secret".

3. Expand live-mutable settings only where feature code already resolves values dynamically.

   Existing live groups remain mutable: query safety and hybrid search bounds, chunking, extraction limits/retries, and AI observability content/tag settings. Logging level can be considered for live mutability only if implemented through Spring Boot's `LoggingSystem`; otherwise it should start as read-only.

   Rationale: mutation is safe when downstream services ask `RuntimeSettingsService` for the current value at call time. Startup-bound infrastructure must not be edited unless a dedicated runtime reconfiguration path exists.

4. Treat AI provider bootstrap properties as read-only context.

   `app.model.*` and `spring.ai.openai.*` seed or configure startup/provider defaults. The existing AI profile API is the external management surface for base URL, API key, chat model, embedding model, and embedding dimensions used by knowledge-base workflows. Settings list responses can show masked/configured status for secrets and non-secret startup defaults, but update attempts should direct callers to profile management.

   Rationale: profile changes already handle secret masking, client cache invalidation, and embedding compatibility checks. Duplicating those controls in runtime settings would create conflicting behavior.

5. Mask sensitive values consistently.

   Sensitive entries include `app.model.api-key`, `spring.ai.openai.api-key`, `spring.neo4j.authentication.password`, and OTLP authorization headers. Read responses should return `currentValue` and `defaultValue` as a masked descriptor such as configured/not configured, not the raw secret. Updates to sensitive startup-bound entries are rejected in this change.

6. Preserve persisted override semantics.

   Mutable live settings keep the current override behavior: valid updates persist in Neo4j, reads report `source=override`, and clearing an override falls back to the startup default. Read-only settings can be listed but cannot be updated or cleared.

7. Document profile-specific overrides as startup defaults.

   The catalog should reflect the active application environment after Spring profile resolution. Documentation should explain that `application-openai.properties`, `application-lm_studio.properties`, and `application-langfuse.properties` can change the startup defaults shown by the runtime settings API.

## Risks / Trade-offs

- [Risk] Operators may expect read-only startup settings to change immediately because they appear in the management UI. -> Mitigation: expose explicit update mode, `mutable=false`, `liveApplied=false`, and a restart/read-only reason.
- [Risk] Sensitive defaults could leak through generic serialization. -> Mitigation: all sensitive definitions use a masked value mapper and tests assert raw secrets are absent.
- [Risk] The catalog can drift from `application.properties`. -> Mitigation: add tests that assert expected keys from application property groups are represented with the intended mutability and sensitivity.
- [Risk] Adding too many duplicate Spring alias keys can make the frontend noisy. -> Mitigation: group derived aliases separately or mark them as bootstrap/derived context; prefer backend-owned keys for mutable behavior.

## Migration Plan

1. Extend runtime setting definitions with update mode, restart/read-only reason, optional display metadata, and masked value support.
2. Add definitions for the remaining relevant `application.properties` groups, defaulting startup-bound infrastructure to read-only.
3. Keep existing persisted overrides valid without migration because keys and storage format remain unchanged.
4. Add or update tests for catalog coverage, mutability rejection, sensitive masking, and typed live accessors.
5. Update README and synchronized contributor guidance for the expanded externally manageable settings model.

Rollback is to remove newly added catalog definitions and response metadata while leaving existing `RuntimeSettingOverride` nodes intact. Existing live settings continue to work because their keys and accessors are unchanged.

## Open Questions

- Should `logging.level.root` be live-editable through Spring Boot `LoggingSystem`, or should it remain read-only until a broader logging management feature is planned?
- Should the runtime settings API expose every derived `spring.ai.openai.*` alias, or only the backend-owned `app.model.*` defaults plus a short note that Spring AI aliases are derived from them?
