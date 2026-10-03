package io.github.vfedoriv.graphrag.schemas.drafts.contracts;

/** Scoped authoring facts for downstream schema workflows. */
public interface DraftAdmissions {
    Draft requireOwned(String knowledgeBaseId, String draftId);
    Draft requireMutable(String knowledgeBaseId, String draftId, long revision);

    record Draft(String id, String knowledgeBaseId, long revision, Long persistenceVersion,
                 String targetName, int targetVersion, String aggregateRevisionId,
                 String guidanceJson, boolean open, String publicationSchemaId, String publicationContentHash) { }
}
