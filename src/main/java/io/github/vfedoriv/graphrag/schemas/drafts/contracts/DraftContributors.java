package io.github.vfedoriv.graphrag.schemas.drafts.contracts;

import java.util.Set;

/** Successful source evidence, including the legacy document-ID fallback. */
public interface DraftContributors {
    Contributors contributors(String knowledgeBaseId, String draftId);

    record Contributors(Set<String> sha256s, Set<String> historicalDocumentIds) {
        public Contributors {
            sha256s = Set.copyOf(sha256s);
            historicalDocumentIds = Set.copyOf(historicalDocumentIds);
        }
    }
}
