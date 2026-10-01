package io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentMigrationPreparation;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentPreparation;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReprocessingDocumentPreparationAdapter implements ReprocessingDocumentPreparation {
    private final DocumentMigrationPreparation documents;

    public ReprocessingDocumentPreparationAdapter(DocumentMigrationPreparation documents) {
        this.documents = documents;
    }

    @Override
    public List<Summary> allOwned(String knowledgeBaseId) {
        return documents.allOwned(knowledgeBaseId).stream().map(this::summary).toList();
    }

    @Override
    public List<Summary> ownedIds(String knowledgeBaseId, List<String> documentIds) {
        return documents.ownedIds(knowledgeBaseId, documentIds).stream().map(this::summary).toList();
    }

    @Override
    public String targetRevision(Profile profile) {
        return documents.targetRevision(profile(profile));
    }

    @Override
    public Identity identity(Profile profile) {
        DocumentMigrationPreparation.Identity identity = documents.identity(profile(profile));
        return new Identity(identity.embeddingSpaceId(), identity.targetRevision());
    }

    @Override
    public Inspection inspect(String knowledgeBaseId, Profile profile) {
        DocumentMigrationPreparation.Inspection inspection = documents.inspect(knowledgeBaseId, profile(profile));
        DocumentReprocessing.ChunkTarget chunk = inspection.chunkTarget();
        ReprocessingDocumentExecutor.ChunkTarget target = chunk == null ? null : new ReprocessingDocumentExecutor.ChunkTarget(
            chunk.strategyName(), chunk.strategyRevision(), chunk.targetTokens(), chunk.overlapTokens(),
            chunk.hardCharacterLimit(), chunk.parentTargetTokens(), chunk.parentHardCharacterLimit(),
            chunk.parentMaxPages(), chunk.contextHeaderMaxTokens(), chunk.contextHeaderMaxCharacters(),
            chunk.tokenizerId(), chunk.tokenizerRevision(), chunk.tokenCountMode(),
            chunk.representationRevision(), chunk.settingsHash());
        return new Inspection(inspection.targetRevision(), inspection.embeddingSpaceId(), target,
            inspection.blocker() == null ? null : new Blocker(inspection.blocker().code(), inspection.blocker().message()));
    }

    @Override
    public List<Prepared> prepare(List<Summary> sources, Profile profile, Map<String, Object> options) {
        List<DocumentMigrationPreparation.Summary> mapped = sources.stream().map(source ->
            new DocumentMigrationPreparation.Summary(source.id(), source.originalFilename(), source.sha256(),
                source.uploadedAt(), source.status(), source.contentType(), source.processingDefaultsJson())).toList();
        return documents.prepare(mapped, profile(profile), options).stream().map(prepared -> {
            DocumentReprocessing.DocumentTarget target = prepared.target();
            return new Prepared(summary(prepared.document()), Classification.valueOf(prepared.classification().name()),
                new ReprocessingDocumentExecutor.DocumentTarget(target.sourceSha256(), target.parserId(), target.parserRevision(),
                    target.fileFormat(), target.effectiveChunkerRevision(), target.effectiveProcessingOptions()));
        }).toList();
    }

    private Summary summary(DocumentMigrationPreparation.Summary source) {
        return new Summary(source.id(), source.originalFilename(), source.sha256(), source.uploadedAt(),
            source.status(), source.contentType(), source.processingDefaultsJson());
    }

    private DocumentMigrationPreparation.Profile profile(Profile source) {
        return new DocumentMigrationPreparation.Profile(source.id(), source.revision(), source.baseUrl(),
            source.embeddingModel(), source.embeddingDimensions(), source.tokenizerId());
    }
}
