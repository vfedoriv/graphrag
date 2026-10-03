package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;

import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;

import io.github.vfedoriv.graphrag.documents.domain.options.ImmutableDocumentProcessingInput;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentReprocessingFacadeTest {
    private final DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
    private final DocumentProcessingService processing = mock(DocumentProcessingService.class);
    private final KnowledgeBaseService profiles = mock(KnowledgeBaseService.class);
    private final ChunkingService chunking = mock(ChunkingService.class);

    private DocumentReprocessing facade() throws Exception {
        return new DocumentReprocessingFacade(documents, processing, profiles, chunking);
    }

    private DocumentReprocessing.Request activation() {
        return new DocumentReprocessing.Request("kb", "doc", "hash", "scope",
            new DocumentReprocessing.Activation(Map.of("mode", "saved")));
    }

    private void source(String hash) {
        DocumentUploadNode source = new DocumentUploadNode();
        source.setSha256(hash);
        when(documents.findByIdAndKnowledgeBaseId("doc", "kb")).thenReturn(Optional.of(source));
    }

    @Test
    void missingOrReplacedSourcesNeverInvokeProcessing() throws Exception {
        DocumentReprocessing facade = facade();
        when(documents.findByIdAndKnowledgeBaseId("doc", "kb")).thenReturn(Optional.empty());
        DocumentReprocessing.Source requested = new DocumentReprocessing.Source("kb", "doc", "hash");
        assertThat(facade.sourceMatches(requested)).isFalse();
        source("replacement");
        assertThat(facade.sourceMatches(requested)).isFalse();
        verifyNoInteractions(processing, profiles, chunking);
        source("hash");
        assertThat(facade.sourceMatches(requested)).isTrue();
        IllegalStateException lookupFailure = new IllegalStateException("private content");
        when(documents.findByIdAndKnowledgeBaseId("doc", "kb")).thenThrow(lookupFailure);
        assertThatThrownBy(() -> facade.sourceMatches(requested)).isSameAs(lookupFailure);
    }

    @Test
    void activationPreservesOptionsOutcomesAndRestoresProfileScope() throws Exception {
        DocumentReprocessing facade = facade();
        source("hash");
        assertThat(facade.sourceMatches(new DocumentReprocessing.Source("kb", "doc", "hash"))).isTrue();
        DocumentUploadNode processed = new DocumentUploadNode();
        processed.setStatus(DocumentStatus.COMPLETED);
        when(processing.process("doc", true, Map.of("mode", "saved"))).thenAnswer(invocation -> {
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("scope");
            return processed;
        });
        AiProfileContext.withProfile("outer", () -> {
            assertThat(facade.execute(activation()).status()).isEqualTo(DocumentReprocessing.Status.SUCCEEDED);
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("outer");
            processed.setStatus(DocumentStatus.FAILED);
            assertThat(facade.execute(activation())).isEqualTo(new DocumentReprocessing.Result(
                DocumentReprocessing.Status.FAILED, "DOCUMENT_PROCESSING_FAILED"));
            doThrow(new IllegalArgumentException("private content")).when(processing)
                .process("doc", true, Map.of("mode", "saved"));
            assertThat(facade.execute(activation())).isEqualTo(new DocumentReprocessing.Result(
                DocumentReprocessing.Status.FAILED, "IllegalArgumentException"));
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("outer");
        });
        assertThat(AiProfileContext.activeProfileId()).isNull();
    }

    @Test
    void migrationRestoresCapturedOptionsProfileAndRevisionsForSuccessAndFailure() throws Exception {
        DocumentReprocessing facade = facade();
        source("hash");
        assertThat(facade.sourceMatches(new DocumentReprocessing.Source("kb", "doc", "hash"))).isTrue();
        io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode profile = new io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode();
        when(profiles.aiProfile("captured-profile")).thenReturn(profile.facts());
        io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext context =
            io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext.create(
                "recursive", "recursive-v1", 800, 80, 4000,
                new io.github.vfedoriv.graphrag.documents.domain.chunking.Utf8ByteTokenEstimator(), "parser-v1", "representation-v1");
        DocumentReprocessing.ChunkTarget target = new DocumentReprocessing.ChunkTarget(
            "recursive", "recursive-v1", 800, 80, 4000, 1000, 5000, 3, 40, 200,
            "utf8-byte-v1", "tokenizer-v1", "CONSERVATIVE", "representation-v1", "settings");
        DocumentReprocessing.DocumentTarget document = new DocumentReprocessing.DocumentTarget(
            "hash", "text", "parser-v1", "TXT", "effective", Map.of("saved", 12));
        when(chunking.restore(profile.facts(), target, document)).thenReturn(context);
        DocumentReprocessing.Request request = new DocumentReprocessing.Request("kb", "doc", "hash", "scope",
            new DocumentReprocessing.Migration("captured-profile", 17, "space", "schema", "schema-hash", target, document));
        DocumentUploadNode completed = new DocumentUploadNode();
        completed.setStatus(DocumentStatus.COMPLETED);
        when(processing.process(eq("doc"), eq(true), any(ImmutableDocumentProcessingInput.class))).thenAnswer(call -> {
            ImmutableDocumentProcessingInput input = call.getArgument(2);
            assertThat(input.aiProfileId()).isEqualTo("captured-profile");
            assertThat(input.aiProfileRevision()).isEqualTo(17);
            assertThat(input.embeddingSpaceId()).isEqualTo("space");
            assertThat(input.schemaId()).isEqualTo("schema");
            assertThat(input.schemaContentHash()).isEqualTo("schema-hash");
            assertThat(input.chunkingContext()).isSameAs(context);
            assertThat(input.processingOptions().detection().parserId()).isEqualTo("text");
            assertThat(input.processingOptions().detection().fileFormat()).isEqualTo("TXT");
            assertThat(input.processingOptions().effectiveOptions()).containsEntry("saved", 12);
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("scope");
            return completed;
        });
        AiProfileContext.withProfile("outer", () -> {
            assertThat(facade.execute(request).status()).isEqualTo(DocumentReprocessing.Status.SUCCEEDED);
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("outer");
            doThrow(new IllegalStateException("private")).when(processing)
                .process(eq("doc"), eq(true), any(ImmutableDocumentProcessingInput.class));
            assertThat(facade.execute(request).failureCategory()).isEqualTo("IllegalStateException");
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("outer");
        });
    }
}
