## 1. Runtime Settings Contract

- [x] 1.1 Update runtime setting metadata semantics so `mutable` means API-editable and `liveApplied`/`updateMode` describe application timing.
- [x] 1.2 Add explicit response metadata for override lifecycle state so clients can distinguish live overrides, pending restart changes, and active post-restart values.
- [x] 1.3 Update non-mutable error handling to reject only non-editable settings, not every `restart-required` setting.
- [x] 1.4 Ensure lifecycle state is derived or reconciled from current definitions and active values instead of blindly trusting any previously persisted state field.

## 2. Restart-Required Override Support

- [x] 2.1 Load supported persisted restart-required values from Neo4j during startup after deployment-provided Neo4j connectivity is established and before affected behavior is first used, or apply them through explicit runtime reassignment paths.
- [x] 2.2 Add setting definition support for mutable restart-required entries with typed parsers, storage mapping, display mapping, constraints, and restart reasons.
- [x] 2.3 Reclassify only supported non-secret restart-required settings as mutable, leaving application identity, AI profile-managed settings, sensitive settings, Neo4j URI/authentication/credentials/database settings, and non-reassignable pre-Neo4j-applied settings non-mutable.
- [x] 2.4 Ensure list responses show persisted restart-required overrides as desired values while preserving clear active-vs-pending lifecycle metadata.
- [x] 2.5 Ensure restart-required override state changes from pending to active after backend restart when the saved value is loaded as the running value.
- [x] 2.6 Add migration or startup reconciliation for existing `RuntimeSettingOverride` records with missing, stale, or contradictory lifecycle/status data.
- [x] 2.7 Ensure clear resets mutable restart-required settings back to startup defaults and reports restart semantics correctly.

## 3. Logging Editability

- [x] 3.1 Make `logging.level.root` mutable through the runtime settings catalog.
- [x] 3.2 Prefer live logging level application through the logging subsystem; if not implemented live, expose it as restart-required with pending lifecycle state.
- [x] 3.3 Add validation for accepted logging level values.

## 4. Bulk Update Behavior

- [x] 4.1 Allow bulk update requests to contain a mix of live and restart-required mutable settings.
- [x] 4.2 Preserve atomic bulk validation and persistence when any submitted setting is invalid, duplicate, unknown, or non-mutable.
- [x] 4.3 Return updated setting representations in request order with correct live, pending, or active lifecycle metadata.

## 5. Tests

- [x] 5.1 Update `RuntimeSettingsServiceTest` coverage for mutable restart-required update, clear, list, pending-to-active transition, stale lifecycle-state reconciliation, and non-mutable rejection behavior.
- [x] 5.2 Update `RuntimeSettingsControllerTest` coverage for single and bulk restart-required updates and RFC 7807 validation failures.
- [x] 5.3 Add startup/default resolution coverage proving supported persisted restart-required overrides are used after restart or bootstrap simulation.
- [x] 5.4 Add catalog classification tests proving sensitive, profile-managed, application identity, Neo4j connectivity, and non-reassignable pre-Neo4j-applied settings remain non-mutable.
- [x] 5.5 Add tests for root logging level editability, validation, persistence, and live or pending metadata.
- [x] 5.6 Run `./mvnw test`.

## 6. Documentation

- [x] 6.1 Update `README.md` to describe editable restart-required settings, pending restart behavior, active-after-restart behavior, logging editability, and the updated meaning of `mutable`.
- [x] 6.2 Update `AGENTS.md` and `CLAUDE.md` to keep overlapping runtime settings guidance synchronized, including pre-Neo4j-applied settings without runtime reassignment and Neo4j connectivity remaining deployment-managed.
- [x] 6.3 Run `openspec status --change "allow-restart-required-setting-overrides"` and confirm all required artifacts are complete.
