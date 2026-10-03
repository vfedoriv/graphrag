package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentFormatDetection;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionSet;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import io.github.vfedoriv.graphrag.documents.domain.options.ImmutableDocumentProcessingInput;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseProfiles;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Transitional documents-owned bridge to the legacy processing implementation. */
@Service
public class DocumentReprocessingFacade implements DocumentReprocessing {
    private final DocumentUploadRepository documents;
    private final DocumentProcessingService processing;
    private final KnowledgeBaseProfiles profiles;
    private final ChunkingService chunking;

    public DocumentReprocessingFacade(DocumentUploadRepository documents, DocumentProcessingService processing,
                                     KnowledgeBaseProfiles profiles, ChunkingService chunking) {
        this.documents = documents;
        this.processing = processing;
        this.profiles = profiles;
        this.chunking = chunking;
    }

    @Override
    public boolean sourceMatches(Source requested) {
        DocumentUploadNode source = documents.findByIdAndKnowledgeBaseId(
            requested.documentId(), requested.knowledgeBaseId()).orElse(null);
        return source != null && requested.expectedSourceSha256().equals(source.getSha256());
    }

    @Override
    public Result execute(Request request) {
        try {
            DocumentUploadNode processed;
            if (request.target() instanceof Migration migration) {
                ProfileFacts profile = profiles.aiProfile(migration.aiProfileId());
                ChunkingContext context = chunking.restore(profile, migration.chunkTarget(), migration.documentTarget());
                DocumentTarget target = migration.documentTarget();
                DocumentProcessingOptionSet options = new DocumentProcessingOptionSet(
                    new DocumentFormatDetection(target.parserId(), target.fileFormat()),
                    target.effectiveProcessingOptions(), Map.of(), target.effectiveProcessingOptions());
                ImmutableDocumentProcessingInput input = new ImmutableDocumentProcessingInput(
                    migration.aiProfileId(), migration.aiProfileRevision(), migration.embeddingSpaceId(),
                    migration.schemaId(), migration.schemaContentHash(), options, context);
                processed = AiProfileContext.withProfile(request.profileScopeId(),
                    () -> processing.process(request.documentId(), true, input));
            } else {
                Activation activation = (Activation) request.target();
                processed = AiProfileContext.withProfile(request.profileScopeId(),
                    () -> processing.process(request.documentId(), true, activation.processingOptions()));
            }
            return processed.getStatus() == DocumentStatus.COMPLETED
                ? new Result(Status.SUCCEEDED, null)
                : new Result(Status.FAILED, "DOCUMENT_PROCESSING_FAILED");
        } catch (Exception exception) {
            return new Result(Status.FAILED, exception.getClass().getSimpleName());
        }
    }
}
