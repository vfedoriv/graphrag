## 1. Activation Semantics

- [x] 1.1 Update schema activation service flow to enforce single active schema per knowledge base.
- [x] 1.2 Implement repository/data-access update that deactivates sibling schemas in the same knowledge base when target schema is activated.
- [x] 1.3 Make activation idempotent when the target schema is already active.
- [x] 1.4 Ensure activation/deactivation state change runs in one transactional boundary.

## 2. Validation Contract Alignment

- [x] 2.1 Update active-schema resolution path used by query validation to rely on unambiguous single active schema invariant.
- [x] 2.2 Add or adjust error handling for invalid activation transitions to prevent partial multi-active states.

## 3. Test Coverage

- [x] 3.1 Add service or repository tests verifying activation deactivates all siblings in the same knowledge base.
- [x] 3.2 Add tests verifying activation does not affect schemas in other knowledge bases.
- [x] 3.3 Add test verifying repeated activation of already active schema is successful and does not create side effects.
- [x] 3.4 Add integration test asserting final state has exactly one active schema after activation request.

## 4. Regression Verification

- [x] 4.1 Run focused tests for schema activation and schema lookup behavior.
- [x] 4.2 Run full test suite with `./mvnw test` and confirm no regressions.
