## 1. Validator Extraction

- [x] 1.1 Add context-aware helper methods in `CypherValidationService` to extract labels from node patterns and relationship types from relationship patterns.
- [x] 1.2 Update relationship extraction to capture every type in union patterns such as `[:TYPE_A|TYPE_B]` and `[r:TYPE_A|TYPE_B]`.
- [x] 1.3 Keep property reference extraction behavior for simple qualified references and preserve existing infrastructure property allow-list handling.
- [x] 1.4 Ensure validation errors remain specific: unknown labels report only labels, and unknown relationship types report only relationship types.

## 2. Unit Coverage

- [x] 2.1 Add a regression test for the grease recommendation query shape where `HAS_GREASE_RECOMMENDATION|REQUIRES_GREASE` must not produce `Unknown label`.
- [x] 2.2 Add tests for valid and invalid relationship unions, including aliased relationships.
- [x] 2.3 Add tests for valid and invalid node labels, including backtick-quoted label syntax supported by the validator.
- [x] 2.4 Add tests for valid and invalid qualified property references.
- [x] 2.5 Add tests covering mixed query shapes with multiple node patterns, relationship direction variants, and `RETURN type(r)`.

## 3. Verification

- [x] 3.1 Run `./mvnw -Dtest=CypherValidationServiceTest test`.
- [x] 3.2 Run `./mvnw test` or document any environment limitation that prevents the full suite from running.
