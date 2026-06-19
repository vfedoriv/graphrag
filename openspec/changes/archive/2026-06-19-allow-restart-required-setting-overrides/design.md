## Context

`RuntimeSettingsService` currently has one persistence path for mutable live overrides and one catalog path for visible startup-bound configuration. The existing implementation sets `mutable=false` for every `restart-required` definition, so update and clear requests are rejected before validation even when a property could safely be edited as a pending restart change.

The existing design decision that still matters is that Spring Boot infrastructure is not live-reconfigured by this API. Neo4j connectivity, multipart limits, actuator exposure, tracing exporters, Spring AI bootstrap, and similar infrastructure are created or bound at startup. Properties that are consumed before Neo4j-backed overrides can be loaded must remain read-only unless the application has a safe runtime reassignment path for the already-created component. Neo4j connection properties are the clearest bootstrap boundary: because the runtime settings store is Neo4j, Neo4j URI, credentials, and database selection must remain read-only in the UI and continue to be changed through environment variables, Docker Compose, or deployment configuration. The corrected model should therefore separate editability from live application:

- `mutable=true`: the backend accepts validated updates and clears for this allowlisted setting.
- `liveApplied=true`: the running process reads the override through typed accessors and uses it on subsequent work.
- `updateMode=restart-required`: the override is accepted and visible now, but it is not applied to the running infrastructure until restart.

## Goals / Non-Goals

**Goals:**

- Let the frontend edit selected restart-required settings through the same settings API used for live settings.
- Preserve typed validation, explicit allowlisting, secret masking, and atomic bulk update behavior.
- Make pending state explicit enough for the frontend to show that a saved value is waiting for restart or activation.
- Keep settings that are coupled to identity, secrets, profile management, pre-Neo4j bootstrap binding without runtime reassignment, or unsafe infrastructure changes non-mutable.
- Provide an implementation path for applying restart-required overrides on application startup.
- Make root logging level editable through the runtime settings API.

**Non-Goals:**

- Runtime reconfiguration of Spring Boot infrastructure that is currently startup-bound.
- Editing arbitrary `spring.*`, `management.*`, system, or environment properties outside the catalog.
- Replacing AI profile management with raw provider property edits.
- Persisting edits back into `application.properties`.
- Building the frontend management screen in this change unless a frontend exists in the target branch.

## Decisions

1. Treat mutability as API editability, not live application.

   Rationale: frontend controls need to know whether the user can submit a change. The existing `liveApplied` and `updateMode` fields already describe when the change takes effect. Keeping `mutable=false` for restart-required entries conflates "cannot change" with "requires restart".

   Alternative considered: add a separate `editable` field while preserving old `mutable` semantics. Rejected because the existing API already exposes `mutable` as the natural frontend editability flag, and adding a near-duplicate would create ambiguous client behavior.

2. Persist restart-required changes as override records with pending restart semantics.

   Rationale: the backend can validate and store the operator's intended value immediately, return it in list responses, and allow clearing it before restart. The running infrastructure continues using its current bound value until restart.

   The response should distinguish:

   - startup default: no override exists;
   - live override: override exists and applies to current runtime behavior;
   - restart-pending override: override exists for a restart-required setting and differs from the current startup-bound value.

   The response should include an explicit status for restart-required overrides. Use a dedicated state such as `pending` while the saved value differs from the value active in the running process. After restart, once the saved value is the active startup value, the state should no longer be `pending`; it should transition to an active override/default state. The exact field name can be chosen during implementation, but the contract must let clients render pending state without inferring it from strings.

   Existing override records may predate this lifecycle model or may contain extra Neo4j properties from earlier experiments. The implementation should not trust persisted lifecycle/status fields as the source of truth when they can contradict the current catalog. Lifecycle state should be derived or normalized from the current setting definition, persisted value, active runtime value, and update mode. Any persisted state field that is retained for query efficiency must be repaired when the setting is read or when startup reconciliation runs.

3. Keep pre-Neo4j-applied properties read-only unless they can be reassigned safely at runtime.

   Rationale: the persisted override store depends on Neo4j, so UI-managed persisted overrides are not available during the earliest property binding phase. Any property already loaded from property files, environment variables, or deployment configuration before Neo4j starts must be treated as read-only unless the app can explicitly reassign it in the running process after the override is loaded.

   Neo4j URI, username, password, and database selection stay read-only and must be changed through environment variables, Docker Compose, Kubernetes configuration, or another deployment-level configuration source. Other pre-Neo4j-applied settings follow the same rule: read-only by default, mutable only when there is a specific runtime reassignment path.

   This creates a practical constraint: a restart-required setting should be mutable only if the application can load it from Neo4j during startup after the connection is established but before the affected behavior is first used, or if the affected component has an explicit runtime reassignment path invoked after the override is loaded. Keys that must be known before Neo4j is reachable and cannot be reassigned remain non-mutable.

4. Classify restart-required keys conservatively.

   Candidate mutable restart-required keys are non-secret operational settings that are not application identity, not AI profile-managed, not consumed irreversibly before Neo4j-backed overrides load, and not so tightly coupled that changing them can orphan persisted data or prevent the app from reaching the settings store. Examples to evaluate during implementation include multipart limits, tracing sampling/export toggles, OTLP non-secret endpoint/header values, Spring AI non-secret timeout/logging options, and other operational settings that can consume persisted overrides on the next launch or through an explicit runtime reassignment path.

   Settings that should remain non-mutable include `spring.application.name`, AI profile-managed provider settings, sensitive values such as API keys/passwords/authorization headers, connectivity settings needed to reach the runtime settings store itself such as Neo4j URI, Neo4j credentials, and database selection, and any other setting that has already been applied before Neo4j starts and cannot be reassigned safely.

5. Make logging level editable.

   Rationale: root logging level is a useful operator control and does not have the same compatibility risks as provider secrets or Neo4j bootstrap configuration. Implementation should prefer live application through Spring's logging infrastructure if available in the running app. If the implementation cannot safely apply it live, it may be exposed as a mutable restart-required setting with pending state, but the catalog must still mark it editable.

6. Keep bulk updates atomic across live and restart-required settings.

   Rationale: the frontend should be able to submit a mixed settings form. The service should parse and validate every submitted value first, reject duplicate/non-allowlisted/non-mutable/invalid entries, and persist nothing unless the entire request is valid.

7. Keep typed accessors limited to live settings.

   Rationale: feature services should not start reading startup-bound pending values during the current process. The existing typed accessors continue to resolve live settings. Restart-required values affect those startup-bound components only after restart.

## Risks / Trade-offs

- [Risk] Persisted overrides cannot be read early enough to affect startup-bound infrastructure. -> Mitigation: pre-Neo4j-applied properties stay deployment-managed and read-only unless there is a safe runtime reassignment path; mark only keys that can consume persisted overrides after Neo4j is reachable as mutable.
- [Risk] Users may expect restart-required edits to apply immediately. -> Mitigation: expose explicit `pending` state and keep `liveApplied=false` for restart-required settings until the saved value is active after restart.
- [Risk] A persisted restart-required value can prevent the app from starting. -> Mitigation: validate types/ranges on update, keep dangerous keys non-mutable, and support clearing or bypassing persisted overrides operationally.
- [Risk] Sensitive settings become writable by accident when changing mutability semantics. -> Mitigation: require every mutable restart-required definition to be explicitly declared and covered by tests; keep sensitive read-only update modes rejected.
- [Risk] Documentation drifts from the new meaning of `mutable`. -> Mitigation: update README, AGENTS.md, and CLAUDE.md in the implementation change.

## Migration Plan

1. Extend setting definitions to distinguish non-mutable restart-required inventory entries from mutable restart-required override entries.
2. Add response metadata for restart-required override state and update API tests to cover frontend-visible semantics.
3. Add reconciliation for existing `RuntimeSettingOverride` records so missing, stale, or contradictory lifecycle/status data is derived or corrected from the current catalog and active runtime value.
4. Load supported restart-required overrides from Neo4j during startup after deployment-provided Neo4j connectivity is established and before affected behavior is first used, or apply them through explicit runtime reassignment paths.
5. Reclassify only safe supported restart-required keys as `mutable=true`; keep Neo4j connectivity, credentials, database selection, and other non-reassignable pre-Neo4j-applied properties read-only.
6. Make root logging level editable, preferably live-applied through the logging subsystem.
7. Update bulk update and clear behavior so live and restart-required mutable settings share validation and atomic persistence.
8. Update documentation and synchronized contributor guidance.

Rollback is to mark the newly mutable restart-required definitions non-mutable again and disable the startup override source. Existing override records can remain in storage but should be ignored for non-mutable definitions.

## Open Questions

- Which exact response field name should represent the lifecycle state: `state`, `applyState`, `overrideState`, or another frontend-facing name?
- Should logging level be implemented as live-applied through the logging subsystem in this change, or initially editable as restart-required if live logging control is not available in the current app wiring?
