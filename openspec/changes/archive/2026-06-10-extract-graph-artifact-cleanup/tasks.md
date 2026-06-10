## 1. Cleanup Component

- [x] 1.1 Create a dedicated graph artifact cleanup component for document-scoped derived artifacts
- [x] 1.2 Move document replacement/deletion cleanup Cypher from `DocumentUploadService` into the cleanup component
- [x] 1.3 Move successful extraction cleanup Cypher from `GraphExtractionService` into the cleanup component
- [x] 1.4 Define typed cleanup result records for document cleanup and extraction-run cleanup counts

## 2. Service Integration

- [x] 2.1 Update `DocumentUploadService.replace` to delegate derived artifact cleanup to the cleanup component
- [x] 2.2 Update `DocumentUploadService.delete` to delegate derived artifact cleanup to the cleanup component
- [x] 2.3 Update `GraphExtractionService` to delegate post-success run cleanup to the cleanup component
- [x] 2.4 Keep existing transaction boundaries and logging semantics behavior-compatible

## 3. Verification

- [x] 3.1 Add focused tests for cleanup result count mapping and null-row handling
- [x] 3.2 Update existing document upload and graph extraction cleanup tests for the new component boundary
- [x] 3.3 Run `./mvnw test`
