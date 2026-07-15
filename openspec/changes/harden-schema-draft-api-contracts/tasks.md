## 1. Typed Contract Foundations

- [ ] 1.1 Add the bounded generic page response and explicit OpenAPI schema handling for each candidate/outcome specialization
- [ ] 1.2 Add the canonical draft guidance request/response contract and a legacy guidance reader covering all currently persisted shapes
- [ ] 1.3 Add the typed candidate response with separately named recommendation and effective persistent review states
- [ ] 1.4 Define version-two metric, applicability, advisory-status, reason, evidence, and reproducibility response contracts
- [ ] 1.5 Add version-one evaluation result adapters and advance the new evaluation contract revision and reuse boundary to version two

## 2. Guidance Read and Write Flow

- [ ] 2.1 Replace generic create and guidance-update inputs with validated typed guidance while preserving optimistic revision behavior
- [ ] 2.2 Return canonical guidance, revision, and fingerprint from draft create, list, detail, metadata update, and guidance update responses
- [ ] 2.3 Add integration fixtures proving legacy direct, instructions-only, and wrapped guidance values read correctly and canonicalize on update
- [ ] 2.4 Add validation tests proving unknown or invalid guidance does not mutate draft state

## 3. Candidate and Outcome Response Wiring

- [ ] 3.1 Return a typed candidate page and resolve each candidate's latest persistent decision state without hiding rejected evidence
- [ ] 3.2 Convert analysis source outcomes to the standard embedded page envelope and remove the legacy parallel count field
- [ ] 3.3 Convert evaluation aggregate/outcome metrics and advisory data to typed responses, including deterministic legacy defaults
- [ ] 3.4 Convert evaluation outcomes to the standard embedded page envelope and remove the legacy parallel count field
- [ ] 3.5 Convert reprocessing items to the standard embedded page envelope and remove the legacy parallel count field

## 4. Contract Verification

- [ ] 4.1 Add controller and generated OpenAPI assertions for guidance, candidates, metric enums, advisory results, and specialized page schemas
- [ ] 4.2 Add integration coverage for empty, first, later, and out-of-range pages while aggregate counts remain run-wide
- [ ] 4.3 Add regression tests proving typed mapping never logs guidance, candidate payloads, schemas, model responses, or evaluation evidence content
- [ ] 4.4 Run the focused schema-draft and reprocessing tests, then run `./mvnw test`
- [ ] 4.5 Run `graphify update .` after implementation and review the resulting scoped graph changes
