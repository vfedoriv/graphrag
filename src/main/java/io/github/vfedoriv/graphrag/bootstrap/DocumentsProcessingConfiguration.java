package io.github.vfedoriv.graphrag.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.documents.application.processing.ChunkPreparationStage;
import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingOptionsRegistry;
import io.github.vfedoriv.graphrag.documents.application.processing.EmbeddingPersistenceStage;
import io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionService;
import io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionStage;
import io.github.vfedoriv.graphrag.documents.application.processing.ProcessingJsonCodec;
import io.github.vfedoriv.graphrag.documents.application.processing.ProcessingOptionResolver;
import io.github.vfedoriv.graphrag.documents.application.processing.SourceParsingStage;
import io.github.vfedoriv.graphrag.documents.domain.processing.ChunkMetadataFactory;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkEffects;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assembles stages without adding transactions around external effects. */
@Configuration(proxyBeanMethods = false)
public class DocumentsProcessingConfiguration {
    @Bean
    ProcessingJsonCodec processingJsonCodec(ObjectMapper mapper) {
        return new ProcessingJsonCodec(mapper);
    }

    @Bean
    ProcessingOptionResolver processingOptionResolver(DocumentProcessingOptionsRegistry registry, ProcessingJsonCodec codec) {
        return new ProcessingOptionResolver(registry, codec);
    }

    @Bean
    SourceParsingStage sourceParsingStage(DocumentUploadService upload, DocumentParsingService parsing) {
        return new SourceParsingStage(upload, parsing);
    }

    @Bean
    ChunkPreparationStage chunkPreparationStage(ChunkingService chunking) {
        return new ChunkPreparationStage(chunking, new ChunkMetadataFactory());
    }

    @Bean
    EmbeddingPersistenceStage embeddingPersistenceStage(ProfileScopedAiClientResolver resolver,
        EmbeddingCompatibility compatibility, DocumentChunkEffects chunks, ChunkingService chunking,
        ProcessingJsonCodec codec) {
        return new EmbeddingPersistenceStage(resolver, compatibility, chunks, chunking, codec);
    }

    @Bean
    GraphExtractionStage graphExtractionStage(GraphExtractionService extraction) {
        return new GraphExtractionStage(extraction);
    }
}
