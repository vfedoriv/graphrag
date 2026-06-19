## 1. Persistence Compatibility

- [x] 1.1 Add a targeted runtime setting override repository operation that sets missing `version` properties on existing `RuntimeSettingOverride` nodes.
- [x] 1.2 Invoke the version backfill before persisted restart-required overrides are read or lifecycle reconciliation can save existing override records.
- [x] 1.3 Keep the backfill idempotent so already-versioned records are not modified on subsequent loads.

## 2. Verification

- [x] 2.1 Add a runtime settings service regression test proving version backfill runs before persisted overrides are read.
- [x] 2.2 Run focused runtime settings tests.
- [x] 2.3 Run the full Maven test suite.
- [x] 2.4 Refresh the code graph after implementation.
