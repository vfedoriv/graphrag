package io.github.vfedoriv.graphrag.schemas.reprocessing.contracts;

/** Registry activation invokes this after committing its active-schema change. */
public interface SchemaActivationReprocessing {
    void createForActivation(String knowledgeBaseId, String schemaId);
}
