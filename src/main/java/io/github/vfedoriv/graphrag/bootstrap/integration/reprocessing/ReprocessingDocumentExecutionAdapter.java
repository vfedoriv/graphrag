package io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor;
import org.springframework.stereotype.Component;

@Component
public class ReprocessingDocumentExecutionAdapter implements ReprocessingDocumentExecutor {
    private final DocumentReprocessing documents;

    public ReprocessingDocumentExecutionAdapter(DocumentReprocessing documents) {
        this.documents = documents;
    }

    @Override
    public boolean sourceMatches(Source source) {
        return documents.sourceMatches(new DocumentReprocessing.Source(
            source.knowledgeBaseId(), source.documentId(), source.expectedSourceSha256()));
    }

    @Override
    public Result execute(Request request) {
        DocumentReprocessing.Result result = documents.execute(new DocumentReprocessing.Request(
            request.knowledgeBaseId(), request.documentId(), request.expectedSourceSha256(),
            request.profileScopeId(), mapTarget(request.target())));
        Status status = switch (result.status()) {
            case STALE_SOURCE -> Status.STALE_SOURCE;
            case SUCCEEDED -> Status.SUCCEEDED;
            case FAILED -> Status.FAILED;
        };
        return new Result(status, result.failureCategory());
    }

    private DocumentReprocessing.Target mapTarget(Target target) {
        if (target instanceof Activation activation) {
            return new DocumentReprocessing.Activation(activation.processingOptions());
        }
        Migration migration = (Migration) target;
        ChunkTarget chunk = migration.chunkTarget();
        DocumentTarget document = migration.documentTarget();
        return new DocumentReprocessing.Migration(
            migration.aiProfileId(), migration.aiProfileRevision(), migration.embeddingSpaceId(),
            migration.schemaId(), migration.schemaContentHash(),
            new DocumentReprocessing.ChunkTarget(
                chunk.strategyName(), chunk.strategyRevision(), chunk.targetTokens(), chunk.overlapTokens(),
                chunk.hardCharacterLimit(), chunk.parentTargetTokens(), chunk.parentHardCharacterLimit(),
                chunk.parentMaxPages(), chunk.contextHeaderMaxTokens(), chunk.contextHeaderMaxCharacters(),
                chunk.tokenizerId(), chunk.tokenizerRevision(), chunk.tokenCountMode(),
                chunk.representationRevision(), chunk.settingsHash()),
            new DocumentReprocessing.DocumentTarget(
                document.sourceSha256(), document.parserId(), document.parserRevision(), document.fileFormat(),
                document.effectiveChunkerRevision(), document.effectiveProcessingOptions()));
    }
}
