## 1. Response Normalization

- [x] 1.1 Locate the schema example generation response assembly path shared by `/schemas/generate/example` and `/schemas/generate/example/from-file`.
- [x] 1.2 Implement normalization that trims leading/trailing whitespace artifacts from generated `example` text before returning the API response.
- [x] 1.3 Ensure normalization keeps internal JSON content intact and does not change endpoint field names or validation behavior.

## 2. Test Coverage

- [x] 2.1 Add or update test coverage for text-based example generation to assert `example` output has no leading blank-line/newline artifacts.
- [x] 2.2 Add or update test coverage for file-based example generation to assert the same normalization behavior.
- [x] 2.3 Verify tests still confirm that successful responses remain reviewable text and do not persist schemas.

## 3. Verification

- [x] 3.1 Run targeted tests for schema example generation endpoints.
- [x] 3.2 Run full test suite relevant to schema generation flows and confirm no regressions.
