## 1. Discovery Contracts and Configuration

- [ ] 1.1 Add typed source, structured guidance, candidate, evidence, conflict, source-outcome, and discovery-response DTOs with validation and OpenAPI metadata
- [ ] 1.2 Add a typed discovery-limits/settings value covering source count, sizes, chunks, concurrency, and timeouts without ad hoc setting reads
- [ ] 1.3 Define deterministic candidate identities, origin sets, conflict categories, response statuses, and prompt/candidate contract revisions

## 2. Source Preparation

- [ ] 2.1 Implement knowledge-base document ownership validation and binary loading for discovery references
- [ ] 2.2 Implement request-scoped file parsing and pasted-text preparation without persistence
- [ ] 2.3 Implement deterministic analysis chunking, source/chunk identifiers, SHA-256 fingerprints, and limit enforcement
- [ ] 2.4 Add source preparation tests for ownership isolation, unsupported inputs, empty content, limits, and deterministic identifiers

## 3. Typed Candidate Extraction

- [ ] 3.1 Add the candidate extraction prompt factory with structured guidance, evidence coordinates, property/key rules, and privacy-safe revisioning
- [ ] 3.2 Implement the Spring AI structured-output model adapter using a top-level candidate container and portable conversion fallback
- [ ] 3.3 Resolve every source call through the knowledge base active AI profile and existing `AiObservationService` controls
- [ ] 3.4 Add model-adapter tests for valid output, provider-native option selection, conversion failure, invalid candidates, and content-capture behavior

## 4. Deterministic Aggregation

- [ ] 4.1 Implement conservative label, relationship, and property normalization while retaining original proposed identifiers
- [ ] 4.2 Implement compatible candidate union, independent-document support counts, evidence deduplication, and deterministic ordering
- [ ] 4.3 Implement property-type, identity-key, relationship-name/direction, pinned-context, and alias-suggestion conflict classification
- [ ] 4.4 Implement exclusion suppression, required/preferred guidance handling, warning generation, and review-only schema projection
- [ ] 4.5 Add exhaustive deterministic aggregator and schema-projection unit tests, including repeat-order and first-value regression cases

## 5. Application Workflow and APIs

- [ ] 5.1 Implement the synchronous discovery application service with bounded concurrent source calls and ordered result collection
- [ ] 5.2 Implement completed, partial, and all-failed outcome behavior with retryability classifications
- [ ] 5.3 Add JSON and multipart knowledge-base discovery controller operations with RFC 7807 error mapping
- [ ] 5.4 Verify existing single-source schema and example endpoint requests and responses remain unchanged

## 6. Observability and Safety

- [ ] 6.1 Add workflow/source observations and metadata-only operational logs for discovery
- [ ] 6.2 Add tests proving normal logs exclude source text, guidance, prompts, model output, candidates, and generated schema content
- [ ] 6.3 Add bounded-concurrency, timeout, overload, and partial-failure tests

## 7. Integration Verification

- [ ] 7.1 Add controller tests for mixed documents, text, and multipart files plus structured guidance validation
- [ ] 7.2 Add Testcontainers integration coverage for knowledge-base ownership and active-profile resolution
- [ ] 7.3 Add a deterministic multi-source end-to-end fixture covering compatible merges, low support, conflicts, evidence, and review-only output
- [ ] 7.4 Run `./mvnw test` and update API documentation or contributor guidance where the new workflow is exposed

