## 1. Readiness and Migration

- [ ] 1.1 Run versioned retrieval/answer fixtures and record all KB-isolation, citation, recall, regression, deadline, and partial-failure gates.
- [ ] 1.2 Warm and verify lexical indexes for selected deployment knowledge bases.
- [ ] 1.3 Implement and test idempotent exact-equivalence settings migration with advanced-key precedence.

## 2. Breaking Removal

- [ ] 2.1 Migrate remaining reusable services/tests away from public `HybridSearch*` DTOs.
- [ ] 2.2 Remove the hybrid controller route, `HybridSearchService`, public DTOs, and obsolete wiring.
- [ ] 2.3 Remove legacy hybrid properties/catalog entries and `MENTIONS` traversal/fixtures.
- [ ] 2.4 Preserve generic ask behavior and all advanced-search contracts unchanged.

## 3. Documentation and Verification

- [ ] 3.1 Update README and OpenAPI with advanced run submission, polling, cancellation, partial result, and citation examples.
- [ ] 3.2 Synchronize overlapping implementation facts in `AGENTS.md` and `CLAUDE.md`.
- [ ] 3.3 Run repository searches proving no public hybrid contract or obsolete setting references remain.
- [ ] 3.4 Run focused and full credential-free tests plus staging smoke checks, then `graphify update .`.
