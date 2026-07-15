## 1. Navigation Query Foundations

- [ ] 1.1 Add typed analysis, evaluation, reprocessing, eligibility, and draft workflow summary DTOs using the standard page envelope
- [ ] 1.2 Implement shared analysis/evaluation currentness calculations against authoritative draft snapshot, aggregate, and projection state
- [ ] 1.3 Implement a held-out eligibility resolver that traces successful document source revisions from the current aggregate's producing run
- [ ] 1.4 Add bounded deterministic repository page projections and ownership-safe query helpers for workflow summaries

## 2. Analysis History and Lineage

- [ ] 2.1 Add nullable analysis `retryOfRunId` persistence and pass the selected parent only when retry creates a new run
- [ ] 2.2 Add the paginated draft analysis-run history endpoint with counts, timestamps, retryability, lineage, status location, and derived currentness
- [ ] 2.3 Add tests for running-current, aggregate-current, stale, manual-root, retried, legacy-null-lineage, paging, and foreign-draft cases

## 3. Evaluation History and Eligibility

- [ ] 3.1 Add the paginated draft evaluation-run history endpoint with reproducibility revisions, lineage, retryability, and derived currentness
- [ ] 3.2 Add the paginated evaluation-eligible-documents endpoint with draft revision/current aggregate context and typed ineligibility reasons
- [ ] 3.3 Replace evaluation-start's active-source subtraction with the shared authoritative eligibility resolver
- [ ] 3.4 Add tests for contributing, failed, superseded, removed, non-document, eligible, stale-revision, paging, and ownership cases

## 4. Reprocessing Plan History

- [ ] 4.1 Add knowledge-base and optional draft-filtered plan page queries with deterministic newest-first ordering
- [ ] 4.2 Derive plan latest status, target-schema validity, and command-level retryability without loading paged item payloads
- [ ] 4.3 Add the reprocessing-plan history endpoint and tests for filtering, lineage, active-target changes, paging, and foreign drafts

## 5. Draft Workflow Summaries

- [ ] 5.1 Add batched current-analysis, latest-evaluation, and latest-reprocessing projections for a bounded set of draft identifiers
- [ ] 5.2 Populate lightweight workflow references in draft create/list/detail/update responses without embedding outcomes or plan items
- [ ] 5.3 Add integration tests for empty history, running work, stale latest evaluation, latest plan selection, and bounded query behavior on draft lists

## 6. End-to-End Verification

- [ ] 6.1 Add a reload-recovery integration flow that starts durable work, discards returned identifiers, rediscovers it, and resumes detailed polling
- [ ] 6.2 Add regression tests for RFC 7807 errors and metadata-only logging across every new list and eligibility endpoint
- [ ] 6.3 Run the focused schema-draft and reprocessing tests, then run `./mvnw test`
- [ ] 6.4 Run `graphify update .` after implementation and inspect navigation paths for the new controller-service-repository relationships
