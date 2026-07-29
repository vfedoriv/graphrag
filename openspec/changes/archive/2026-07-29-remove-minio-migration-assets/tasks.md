## 1. Remove Transitional Assets

- [x] 1.1 Delete the migration-only and rollback Compose overlays so the repository exposes no supported legacy object-store startup path.
- [x] 1.2 Delete the migration copy/verification script and transition runbook while preserving all archived OpenSpec records.

## 2. Update Active Guidance

- [x] 2.1 Remove migration, rollback-window, source-volume preservation, and legacy object-store references from `README.md`; retain Garage startup, persistence, smoke-test, and backup guidance.
- [x] 2.2 Update overlapping Garage and persistence guidance in `AGENTS.md` and `CLAUDE.md` so both files remain synchronized and describe only active Garage operations.

## 3. Align Tests and Product Contract

- [x] 3.1 Remove migration-asset and legacy-name assertions from `LangfuseGarageComposeConfigurationTest`, and retain or strengthen positive coverage for the pinned Garage service, initializer, volumes, endpoints, credentials, and smoke-test configuration.
- [x] 3.2 Confirm the `ai-observability-monitoring` delta removes the completed migration/rollback requirement and rewrites the surviving local object-storage requirement entirely in Garage terms, ready for sync to the main spec.
- [x] 3.3 Audit active product, operational, contributor, test, and main-spec content for legacy object-store references, excluding OpenSpec change records and generated graph history.

## 4. Verification

- [x] 4.1 Validate default and `langfuse` Compose resolution after deleting the transitional overlays.
- [x] 4.2 Run the Garage Compose configuration test and executable Garage smoke test to verify initialization, authorization, object operations, and persistence remain intact.
- [x] 4.3 Run strict OpenSpec validation, check repository diffs for whitespace errors, and run `graphify update .` after implementation changes.
