## 1. API Contract

- [x] 1.1 Add DTOs for bulk runtime setting update requests with an `updates` array of key/value entries.
- [x] 1.2 Add validation annotations or controller/service checks for missing updates, empty updates, missing keys, missing values, and duplicate keys.
- [x] 1.3 Add `PUT /api/v1/runtime-settings` to `RuntimeSettingsController` returning a list of `RuntimeSettingResponse` values.

## 2. Service Implementation

- [x] 2.1 Add a transactional bulk update method to `RuntimeSettingsService`.
- [x] 2.2 Validate all submitted keys, mutability, and parsed values before saving any override.
- [x] 2.3 Persist all parsed overrides only after validation succeeds.
- [x] 2.4 Return updated setting responses in the same order as the submitted updates.

## 3. Tests

- [x] 3.1 Add controller tests for successful bulk update routing and request validation failures.
- [x] 3.2 Add service tests proving valid bulk updates persist all submitted overrides.
- [x] 3.3 Add service tests proving invalid, read-only, non-allowlisted, duplicate-key, and empty bulk requests leave existing overrides unchanged.
- [x] 3.4 Run focused runtime settings tests and the full Maven test suite if time permits.
