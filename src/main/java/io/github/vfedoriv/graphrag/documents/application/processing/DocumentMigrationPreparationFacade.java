package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentMigrationPreparation;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionSet;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.error.EmbeddingSpaceConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Synchronous reads participate in the caller's existing transactions. */
@Service
public class DocumentMigrationPreparationFacade implements DocumentMigrationPreparation {
    private final DocumentUploadRepository documents;
    private final DocumentChunkRepository chunks;
    private final DocumentProcessingRunRepository runs;
    private final ProcessingOptionResolver options;
    private final ChunkingService chunking;
    private final EmbeddingCompatibility embedding;

    public DocumentMigrationPreparationFacade(DocumentUploadRepository documents, DocumentChunkRepository chunks,
        DocumentProcessingRunRepository runs, DocumentProcessingOptionsRegistry registry, ChunkingService chunking,
        EmbeddingCompatibility embedding, ObjectMapper mapper) {
        this.documents = documents;
        this.chunks = chunks;
        this.runs = runs;
        this.options = new ProcessingOptionResolver(registry, new ProcessingJsonCodec(mapper));
        this.chunking = chunking;
        this.embedding = embedding;
    }

    @Override
    public List<Summary> allOwned(String knowledgeBaseId) {
        return documents.findByKnowledgeBaseIdOrderByUploadedAtDesc(knowledgeBaseId).stream()
            .map(this::summary).toList();
    }

    @Override
    public List<Summary> ownedIds(String knowledgeBaseId, List<String> documentIds) {
        return documentIds.stream().distinct().map(id -> summary(
            documents.findByIdAndKnowledgeBaseId(id, knowledgeBaseId)
                .orElseThrow(() -> new NotFoundException("Document not found in knowledge base: " + id))))
            .toList();
    }

    @Override
    public String targetRevision(Profile captured) {
        return chunking.migrationTargetRevision(profile(captured));
    }

    @Override
    public Identity identity(Profile captured) {
        AiProfileNode profile = profile(captured);
        return new Identity(EmbeddingTarget.derive(profile.getBaseUrl(), profile.getEmbeddingModel(),
            profile.getEmbeddingDimensions(), profile.getTokenizerId() == null ? null : profile.getTokenizerId().value()).id(), chunking.migrationTargetRevision(profile));
    }

    @Override
    public Inspection inspect(String knowledgeBaseId, Profile captured) {
        AiProfileNode profile = profile(captured);
        String revision = null;
        String space = null;
        DocumentReprocessing.ChunkTarget target = null;
        Blocker blocker = null;
        try {
            revision = chunking.migrationTargetRevision(profile);
            space = EmbeddingTarget.derive(profile.getBaseUrl(), profile.getEmbeddingModel(),
            profile.getEmbeddingDimensions(), profile.getTokenizerId() == null ? null : profile.getTokenizerId().value()).id();
            embedding.requireCompatible(knowledgeBaseId, EmbeddingTarget.derive(profile.getBaseUrl(),
                profile.getEmbeddingModel(), profile.getEmbeddingDimensions(), profile.getTokenizerId() == null ? null : profile.getTokenizerId().value()));
            target = chunking.snapshotTarget(profile);
        } catch (RuntimeException exception) {
            blocker = new Blocker(exception instanceof EmbeddingSpaceConflictException
                ? "EMBEDDING_SPACE_INCOMPATIBLE" : "INVALID_MIGRATION_TARGET",
                exception.getMessage() == null ? "The migration target is not usable" : exception.getMessage());
        }
        return new Inspection(revision, space, target, blocker);
    }

    @Override
    public List<Prepared> prepare(List<Summary> sources, Profile captured, Map<String, Object> requested) {
        AiProfileNode profile = profile(captured);
        List<Prepared> prepared = new ArrayList<>();
        for (Summary source : sources) {
            // Adapt captured source metadata for the legacy resolver without new repository reads.
            DocumentUploadNode document = new DocumentUploadNode();
            document.setOriginalFilename(source.originalFilename());
            document.setContentType(source.contentType());
            document.setProcessingDefaultsJson(source.processingDefaultsJson());
            DocumentProcessingOptionSet resolved = options.resolve(document, requested);
            ChunkingContext context = chunking.snapshot(profile, resolved.detection().parserId());
            DocumentReprocessing.DocumentTarget target = new DocumentReprocessing.DocumentTarget(
                source.sha256(), resolved.detection().parserId(), context.parserRevision(),
                resolved.detection().fileFormat(), context.effectiveRevision().value(), resolved.effectiveOptions());
            boolean hasChunks = !chunks.findByDocumentIdOrderByChunkIndexAsc(source.id()).isEmpty();
            boolean outdated = !hasChunks || isOutdated(source, target.effectiveChunkerRevision());
            Classification classification = !hasChunks ? Classification.NO_CHUNKS
                : outdated ? Classification.OUTDATED : Classification.CURRENT;
            prepared.add(new Prepared(source, classification, target));
        }
        return List.copyOf(prepared);
    }

    private boolean isOutdated(Summary source, String revision) {
        if (chunks.findByDocumentIdOrderByChunkIndexAsc(source.id()).isEmpty()) return true;
        return runs.findByDocumentIdOrderByStartedAtAsc(source.id()).stream()
            .filter(DocumentProcessingRunNode::isActiveCompleted)
            .filter(run -> run.getStatus() == DocumentProcessingRunStatus.COMPLETED)
            .filter(run -> source.sha256().equals(run.getSourceSha256()))
            .noneMatch(run -> revision.equals(run.getEffectiveChunkerRevision()));
    }

    private Summary summary(DocumentUploadNode source) {
        return new Summary(source.getId(), source.getOriginalFilename(), source.getSha256(), source.getUploadedAt(),
            source.getStatus() == null ? null : source.getStatus().name(), source.getContentType(), source.getProcessingDefaultsJson());
    }

    private AiProfileNode profile(Profile captured) {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(captured.id());
        profile.setRevision(captured.revision());
        profile.setBaseUrl(captured.baseUrl());
        profile.setEmbeddingModel(captured.embeddingModel());
        profile.setEmbeddingDimensions(captured.embeddingDimensions());
        profile.setTokenizerId(captured.tokenizerId() == null ? null : new TokenizerId(captured.tokenizerId()));
        return profile;
    }
}
