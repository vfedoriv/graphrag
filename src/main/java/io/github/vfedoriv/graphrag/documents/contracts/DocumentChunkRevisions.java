package io.github.vfedoriv.graphrag.documents.contracts;

/** Read-only revision calculation from a complete supplied configuration. */
public interface DocumentChunkRevisions {
    String calculate(Settings suppliedSettings);

    record Settings(String strategy, int targetTokens, int overlapTokens, int hardCharacterLimit,
        int parentTargetTokens, int parentHardCharacterLimit, int parentMaxPages,
        int contextHeaderMaxTokens, int contextHeaderMaxCharacters, String representationRevision) { }
}
