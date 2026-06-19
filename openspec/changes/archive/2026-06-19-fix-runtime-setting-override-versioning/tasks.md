## 1. Entity State Mapping

- [x] 1.1 Add a Spring Data `@Version` field and getter to `RuntimeSettingOverrideNode`.
- [x] 1.2 Ensure existing tests or fixtures can construct override nodes without manually setting version metadata.

## 2. Runtime Settings Persistence

- [x] 2.1 Add a helper in `RuntimeSettingsService` that loads an existing override node by key or creates a new one when absent.
- [x] 2.2 Update single setting persistence to mutate and save the loaded-or-new override node instead of always saving a fresh assigned-id entity.
- [x] 2.3 Update bulk setting persistence to keep validation atomic while mutating and saving loaded-or-new override nodes after validation succeeds.
- [x] 2.4 Ensure lifecycle-state reconciliation continues to save loaded existing override nodes without changing API response semantics.

## 3. Tests

- [x] 3.1 Update `RuntimeSettingsServiceTest` coverage for repeated updates of the same mutable setting and preservation of the latest value/lifecycle state.
- [x] 3.2 Add or adjust integration coverage if needed to verify runtime setting updates no longer trigger assigned-id new-entity persistence behavior.
- [x] 3.3 Run focused runtime settings tests.
- [x] 3.4 Run `./mvnw test`.
