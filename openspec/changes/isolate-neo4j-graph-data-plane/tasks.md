## 1. Prerequisites and Graph Schema

- [ ] 1.1 Verify PostgreSQL ownership of KBs, schemas, documents, and processing/extraction runs is complete
- [ ] 1.2 Add required knowledge-base, document, and run scope properties to chunk and evidence graph models and write contracts
- [ ] 1.3 Add graph identity, scope, provenance, and vector indexes and plan retirement of operational metadata constraints
- [ ] 1.4 Add tests rejecting missing or inconsistent graph scope

## 2. Processing, Extraction, and Provenance

- [ ] 2.1 Pass `knowledgeBaseId` explicitly through chunking, embedding, chunk persistence, extraction, evidence, and graph-write paths
- [ ] 2.2 Create evidence and provenance without matching operational document or extraction-run nodes
- [ ] 2.3 Preserve chunk-to-evidence and evidence-to-fact relationships plus multi-source attribution
- [ ] 2.4 Restrict SDN repositories to simple chunk persistence and retain `Neo4jClient` adapters for dynamic graph operations

## 3. Search and Compatibility

- [ ] 3.1 Update vector/index selection and embedding compatibility checks to use scoped chunks and relational profile metadata
- [ ] 3.2 Scope hybrid-search candidates inside Neo4j before candidate limits are applied
- [ ] 3.3 Batch-load document metadata from PostgreSQL, preserve ranking, and handle stale hits deterministically
- [ ] 3.4 Add competing-vector tests proving knowledge-base isolation and bounded metadata enrichment

## 4. Cleanup and Verification

- [ ] 4.1 Rewrite run cleanup by `extractionRunId`, document cleanup by document/source-document ID, and KB cleanup by copied scope
- [ ] 4.2 Delete canonical facts only after verifying that no evidence remains
- [ ] 4.3 Verify failure, retry, overwrite, replacement, deletion, multi-source evidence, and orphan cleanup paths
- [ ] 4.4 Run focused graph/search tests, the canonical end-to-end test, and `./mvnw test`
