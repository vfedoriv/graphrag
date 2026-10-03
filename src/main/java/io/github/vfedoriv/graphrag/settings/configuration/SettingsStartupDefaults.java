package io.github.vfedoriv.graphrag.settings.configuration;

/** Owned startup values and non-secret deployment metadata for the settings catalog. */
public record SettingsStartupDefaults(QueryProperties query, ChunkingProperties chunking,
    ExtractionProperties extraction, String documentsRoot, String neo4jDatabase, ModelDefaults model) {
    public record ModelDefaults(String baseUrl, boolean apiKeyConfigured, String embeddingModel,
        int embeddingDimensions, String chatModel) { }
}
