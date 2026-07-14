## 1. Discovery Contracts and Configuration

- [x] 1.1 Add typed source, structured guidance, candidate, evidence, conflict, source-outcome, and discovery-response DTOs with validation and OpenAPI metadata
- [x] 1.2 Add a typed discovery-limits/settings value covering source count, sizes, chunks, concurrency, and timeouts without ad hoc setting reads
- [x] 1.3 Define deterministic candidate identities, origin sets, conflict categories, response statuses, and prompt/candidate contract revisions

## 2. Source Preparation

- [x] 2.1 Implement knowledge-base document ownership validation and binary loading for discovery references
- [x] 2.2 Implement request-scoped file parsing and pasted-text preparation without persistence
- [x] 2.3 Implement deterministic analysis chunking, source/chunk identifiers, SHA-256 fingerprints, and limit enforcement
- [x] 2.4 Add source preparation tests for ownership isolation, unsupported inputs, empty content, limits, and deterministic identifiers

## 3. Typed Candidate Extraction

- [x] 3.1 Add the candidate extraction prompt factory with structured guidance, evidence coordinates, property/key rules, and privacy-safe revisioning
- [x] 3.2 Implement the Spring AI structured-output model adapter using a top-level candidate container and portable conversion fallback
- [x] 3.3 Resolve every source call through the knowledge base active AI profile and existing `AiObservationService` controls
- [x] 3.4 Add model-adapter tests for valid output, provider-native option selection, conversion failure, invalid candidates, and content-capture behavior

## 4. Deterministic Aggregation

- [x] 4.1 Implement conservative label, relationship, and property normalization while retaining original proposed identifiers
- [x] 4.2 Implement compatible candidate union, independent-document support counts, evidence deduplication, and deterministic ordering
- [x] 4.3 Implement property-type, identity-key, relationship-name/direction, pinned-context, and alias-suggestion conflict classification
- [x] 4.4 Implement exclusion suppression, required/preferred guidance handling, warning generation, and review-only schema projection
- [x] 4.5 Add exhaustive deterministic aggregator and schema-projection unit tests, including repeat-order and first-value regression cases

## 5. Application Workflow and APIs

- [x] 5.1 Implement the synchronous discovery application service with bounded concurrent source calls and ordered result collection
- [x] 5.2 Implement completed, partial, and all-failed outcome behavior with retryability classifications
- [x] 5.3 Add JSON and multipart knowledge-base discovery controller operations with RFC 7807 error mapping
- [x] 5.4 Verify existing single-source schema and example endpoint requests and responses remain unchanged

## 6. Observability and Safety

- [x] 6.1 Add workflow/source observations and metadata-only operational logs for discovery
- [x] 6.2 Add tests proving normal logs exclude source text, guidance, prompts, model output, candidates, and generated schema content
- [x] 6.3 Add bounded-concurrency, timeout, overload, and partial-failure tests

## 7. Integration Verification

- [x] 7.1 Add controller tests for mixed documents, text, and multipart files plus structured guidance validation
- [x] 7.2 Add Testcontainers integration coverage for knowledge-base ownership and active-profile resolution
- [x] 7.3 Add a deterministic multi-source end-to-end fixture covering compatible merges, low support, conflicts, evidence, and review-only output
- [x] 7.4 Run `./mvnw test` and update API documentation or contributor guidance where the new workflow is exposed
