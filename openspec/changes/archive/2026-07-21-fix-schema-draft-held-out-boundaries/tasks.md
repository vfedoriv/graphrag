## 1. Eligibility Data Access

- [x] 1.1 Replace the current-aggregate contributing-document query with repository projections for distinct successful source SHA-256 fingerprints and historical document-ID fallback data.
- [x] 1.2 Add repository tests proving fingerprints are resolved only from successful results in the run that produced the draft's current aggregate across `DOCUMENT`, `FILE`, and `TEXT` sources.

## 2. Eligibility and Evaluation Contracts

- [x] 2.1 Extend evaluation eligibility DTOs and OpenAPI contracts with typed evaluation readiness and the `DRAFT_ANALYSIS_REQUIRED` recovery reason while preserving existing page metadata.
- [x] 2.2 Refactor `SchemaDraftEvaluationEligibilityService` to resolve readiness once and classify owned documents by contributed SHA-256, including duplicate document identifiers and compatibility fallback behavior.
- [x] 2.3 Make evaluation start use the shared readiness and fingerprint resolver, reject missing-current-aggregate and contributed-content selections before creating a run, and retain revision/aggregate race checks.

## 3. Behavioral Verification

- [x] 3.1 Add integration tests proving content contributed through `DOCUMENT`, draft-owned `FILE`, and pasted `TEXT` sources is ineligible when uploaded as a matching knowledge-base document.
- [x] 3.2 Add integration tests proving failed or non-contributing source revisions do not exclude matching content and unrelated fingerprints remain eligible.
- [x] 3.3 Add API tests proving a draft without a current aggregate reports `DRAFT_ANALYSIS_REQUIRED`, exposes no selectable evaluation documents, and rejects evaluation start without durable or model side effects.
- [x] 3.4 Add regression tests proving normal knowledge-base document upload and processing do not advance draft revisions or clear current aggregates.
- [x] 3.5 Add race tests proving revision or aggregate changes between eligibility read and evaluation start are still rejected atomically.

## 4. Validation and Documentation

- [x] 4.1 Update API documentation to explain discovery evidence versus held-out content, exact SHA-256 matching semantics, and the re-analysis recovery action.
- [x] 4.2 Run focused schema draft lifecycle, navigation, DTO/OpenAPI, repository, and document-processing tests.
- [x] 4.3 Run the full Maven test suite and refresh the project graph with `graphify update .`.
