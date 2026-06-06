## 1. API Contract

- [x] 1.1 Extend `DocumentUploadResponse` with nullable `localPath` metadata and update OpenAPI schema examples.
- [x] 1.2 Update `DocumentController` response mapping to derive `localPath` from stored document content URIs.
- [x] 1.3 Add `PUT /api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}` for multipart document replacement.
- [x] 1.4 Add `DELETE /api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}` for document deletion with a no-content success response.

## 2. Storage And Cleanup

- [x] 2.1 Add binary storage deletion support for stored document content URIs.
- [x] 2.2 Implement document lookup validation that rejects missing documents and knowledge base mismatches.
- [x] 2.3 Implement document replacement in `DocumentUploadService` with stable document id, duplicate-content conflict checks, metadata reset, and replacement storage failure handling.
- [x] 2.4 Implement reusable document-scoped derived-artifact cleanup for chunks, extraction runs, extracted graph relationships, and obsolete extracted graph nodes.
- [x] 2.5 Implement document deletion that removes the stored binary, derived artifacts, and document record without affecting unrelated documents.

## 3. Error Handling And Observability

- [x] 3.1 Return RFC 7807-compatible not found, conflict, validation, and storage failure errors for update/delete paths.
- [x] 3.2 Log update/delete requests and cleanup outcomes with document id, knowledge base id, and deletion counters without logging document content.

## 4. Tests

- [x] 4.1 Add controller tests for `localPath` in upload, list, and process responses.
- [x] 4.2 Add controller tests for successful replacement, not found replacement, knowledge base mismatch, duplicate replacement, and invalid multipart replacement.
- [x] 4.3 Add service or integration tests proving replacement clears chunks, extraction runs, extracted graph artifacts, status, `processedAt`, and `errorMessage`.
- [x] 4.4 Add controller/service tests for successful deletion, missing document deletion, knowledge base mismatch deletion, and storage deletion failure.
- [x] 4.5 Add integration coverage proving update/delete cleanup is scoped to the target document and preserves unrelated knowledge base data.

## 5. Verification

- [x] 5.1 Run focused document controller/service tests.
- [x] 5.2 Run `./mvnw test`.
- [x] 5.3 Run OpenSpec status for `extend-document-operations` and confirm the change is apply-ready.
