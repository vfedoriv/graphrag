package io.github.vfedoriv.graphrag.documents.application.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentEvaluationPreparation;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DocumentEvaluationPreparationFacadeTest {

    private final DocumentUploadRepository repository = mock(DocumentUploadRepository.class);
    private final DocumentUploadService binaries = mock(DocumentUploadService.class);
    private final DocumentParsingService parser = mock(DocumentParsingService.class);
    private final RuntimeSettingsService runtimeSettings = mock(RuntimeSettingsService.class);
    private final ChunkingService chunking = new ChunkingService(runtimeSettings);
    private final DocumentEvaluationPreparationFacade facade =
        new DocumentEvaluationPreparationFacade(repository, binaries, parser, chunking);

    @Test
    void inspectionReturnsOwnedMetadataAndEmptyForMissingAndForeignDocumentsWithoutReadingContent() {
        Instant uploadedAt = Instant.parse("2026-09-01T10:15:30Z");
        DocumentUploadNode owned = document("owned-document", "kb", uploadedAt);
        owned.setSha256("owned-sha");
        DocumentUploadNode foreign = document("foreign-document", "other-kb", Instant.parse("2026-09-01T10:15:30Z"));
        when(repository.findByIdAndKnowledgeBaseId("owned-document", "kb")).thenReturn(Optional.of(owned));
        when(repository.findByIdAndKnowledgeBaseId("missing-document", "kb")).thenReturn(Optional.empty());
        when(repository.findByIdAndKnowledgeBaseId("foreign-document", "kb")).thenReturn(Optional.empty());

        assertThat(facade.inspectOwned("kb", "owned-document")).contains(
            new DocumentEvaluationPreparation.DocumentMetadata(
                "owned-document", "source.txt", "text/plain", 7L, "owned-sha", uploadedAt));
        assertThat(facade.inspectOwned("kb", "missing-document")).isEmpty();
        assertThat(facade.inspectOwned("kb", "foreign-document")).isEmpty();

        verifyNoInteractions(binaries, parser, runtimeSettings);
        verify(repository, never()).findPageByKnowledgeBaseId("kb", PageRequest.of(0, 1));
    }

    @Test
    void inventoryBoundsPaginationAndReturnsImmutableScopedMetadataWithTotals() {
        Instant firstUpload = Instant.parse("2026-09-01T10:15:30Z");
        Instant secondUpload = Instant.parse("2026-09-02T11:16:31Z");
        DocumentUploadNode first = document("document-1", "kb", firstUpload);
        first.setOriginalFilename("first.pdf");
        first.setContentType("application/pdf");
        first.setSizeBytes(123L);
        first.setSha256("sha-first");
        DocumentUploadNode second = document("document-2", "kb", secondUpload);
        second.setOriginalFilename("second.txt");
        second.setContentType("text/plain");
        second.setSizeBytes(45L);
        second.setSha256("sha-second");
        when(repository.findPageByKnowledgeBaseId("kb", PageRequest.of(0, 100)))
            .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 100), 203L));

        DocumentEvaluationPreparation.DocumentPage page = facade.listOwned("kb", -3, 999);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.totalElements()).isEqualTo(203L);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.documents()).containsExactly(
            new DocumentEvaluationPreparation.DocumentMetadata(
                "document-1", "first.pdf", "application/pdf", 123L, "sha-first", firstUpload),
            new DocumentEvaluationPreparation.DocumentMetadata(
                "document-2", "second.txt", "text/plain", 45L, "sha-second", secondUpload));
        assertThatThrownBy(() -> page.documents().add(page.documents().getFirst()))
            .isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findPageByKnowledgeBaseId("kb", PageRequest.of(0, 100));
        verifyNoInteractions(binaries, parser, runtimeSettings);
    }

    @Test
    void captureOwnedReturnsEmptyForMissingAndForeignDocuments() {
        when(repository.findByIdAndKnowledgeBaseId("missing-document", "kb")).thenReturn(Optional.empty());
        when(repository.findByIdAndKnowledgeBaseId("foreign-document", "kb")).thenReturn(Optional.empty());

        assertThat(facade.captureOwned("kb", "missing-document")).isEmpty();
        assertThat(facade.captureOwned("kb", "foreign-document")).isEmpty();

        verifyNoInteractions(binaries, parser, runtimeSettings);
    }

    @Test
    void capturedSourceUsesOriginalMetadataAndContentAfterDocumentReplacement() throws Exception {
        Instant originalUpload = Instant.parse("2026-09-01T10:15:30Z");
        DocumentUploadNode original = document("document", "kb", originalUpload);
        original.setOriginalFilename("original.pdf");
        original.setContentType("application/pdf");
        original.setSizeBytes(19L);
        original.setSha256("original-sha");
        original.setContentUri("original-storage-uri");
        DocumentUploadNode replacement = document("document", "kb", Instant.parse("2026-09-03T12:17:32Z"));
        replacement.setOriginalFilename("replacement.txt");
        replacement.setContentType("text/plain");
        replacement.setSha256("replacement-sha");
        replacement.setContentUri("replacement-storage-uri");
        byte[] originalBytes = "original bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(repository.findByIdAndKnowledgeBaseId("document", "kb")).thenReturn(Optional.of(original));
        when(binaries.readContent("original-storage-uri")).thenReturn(originalBytes);
        when(parser.parse(eq("original.pdf"), eq("application/pdf"), any(byte[].class))).thenReturn("heldout");
        when(runtimeSettings.chunking()).thenReturn(new RuntimeSettingsService.ChunkingSettings(
            "fixed-character", 100, 0, 4, 100, 100, 2, 100, 100, "evaluation-preparation-test-v1"));

        DocumentEvaluationPreparation.Source captured = facade.captureOwned("kb", "document").orElseThrow();
        assertThat(captured.metadata()).isEqualTo(new DocumentEvaluationPreparation.DocumentMetadata(
            "document", "original.pdf", "application/pdf", 19L, "original-sha", originalUpload));

        doReturn(Optional.of(replacement)).when(repository).findByIdAndKnowledgeBaseId("document", "kb");
        DocumentEvaluationPreparation.PreparedDocument prepared = captured.prepare();

        assertThat(prepared.documentId()).isEqualTo("document");
        assertThat(prepared.chunks()).containsExactly("held", "out");
        verify(repository).findByIdAndKnowledgeBaseId("document", "kb");
        verify(binaries).readContent("original-storage-uri");
        verify(binaries, never()).readContent("replacement-storage-uri");
        verify(parser).parse(eq("original.pdf"), eq("application/pdf"), any(byte[].class));
    }

    @Test
    void capturedSourcePropagatesStorageIOExceptionWithoutParsingOrSplitting() throws Exception {
        DocumentUploadNode owned = document("document", "kb", Instant.parse("2026-09-01T10:15:30Z"));
        owned.setContentUri("original-storage-uri");
        IOException failure = new IOException("storage unavailable");
        when(repository.findByIdAndKnowledgeBaseId("document", "kb")).thenReturn(Optional.of(owned));
        when(binaries.readContent("original-storage-uri")).thenThrow(failure);

        DocumentEvaluationPreparation.Source captured = facade.captureOwned("kb", "document").orElseThrow();

        assertThatThrownBy(captured::prepare).isSameAs(failure);
        verifyNoInteractions(parser, runtimeSettings);
    }

    @Test
    void preparationReadsParsesAndUsesTheExistingChunkSplitterWithImmutableChunks() throws Exception {
        DocumentUploadNode owned = document("document", "kb", Instant.parse("2026-09-01T10:15:30Z"));
        owned.setOriginalFilename("source.txt");
        owned.setContentType("text/plain");
        owned.setContentUri("private-storage-uri");
        byte[] bytes = "raw source bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(repository.findByIdAndKnowledgeBaseId("document", "kb")).thenReturn(Optional.of(owned));
        when(binaries.readContent("private-storage-uri")).thenReturn(bytes);
        when(parser.parse(eq("source.txt"), eq("text/plain"), any(byte[].class))).thenReturn("  abcdef  ");
        when(runtimeSettings.chunking()).thenReturn(new RuntimeSettingsService.ChunkingSettings(
            "fixed-character", 100, 0, 3, 100, 100, 2, 100, 100, "evaluation-preparation-test-v1"));

        DocumentEvaluationPreparation.PreparedDocument prepared = facade.prepareOwned("kb", "document");

        assertThat(prepared.documentId()).isEqualTo("document");
        assertThat(prepared.chunks()).containsExactly("abc", "def");
        assertThatThrownBy(() -> prepared.chunks().add("later"))
            .isInstanceOf(UnsupportedOperationException.class);
        InOrder order = inOrder(repository, binaries, parser, runtimeSettings);
        order.verify(repository).findByIdAndKnowledgeBaseId("document", "kb");
        order.verify(binaries).readContent("private-storage-uri");
        order.verify(parser).parse(eq("source.txt"), eq("text/plain"), any(byte[].class));
        order.verify(runtimeSettings).chunking();
        ArgumentCaptor<byte[]> parsedBytes = ArgumentCaptor.forClass(byte[].class);
        verify(parser).parse(eq("source.txt"), eq("text/plain"), parsedBytes.capture());
        assertThat(parsedBytes.getValue()).containsExactly(bytes);
    }

    private DocumentUploadNode document(String id, String knowledgeBaseId, Instant uploadedAt) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalFilename("source.txt");
        document.setContentType("text/plain");
        document.setSizeBytes(7L);
        document.setSha256("source-sha");
        document.setContentUri("stored-uri");
        document.setUploadedAt(uploadedAt);
        return document;
    }
}
