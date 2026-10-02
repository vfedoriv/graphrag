package io.github.vfedoriv.graphrag.schemas.drafts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.DraftSourceFingerprint;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftBinaryStorage;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftDocumentInputs;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SchemaDraftAnalysisSourceFactoryTest {
    private final DraftDocumentInputs documents = mock(DraftDocumentInputs.class);
    private final DraftBinaryStorage storage = mock(DraftBinaryStorage.class);
    private final RuntimeSettingsService runtimeSettings = mock(RuntimeSettingsService.class);
    private final SchemaDraftAnalysisSourceFactory factory =
        new SchemaDraftAnalysisSourceFactory(documents, storage, runtimeSettings);

    @Test
    void missingAndForeignDocumentsBecomeUnavailableWithoutReadingOrParsing() {
        when(documents.inspectOwned("kb-1", "missing-document")).thenReturn(Optional.empty());
        when(documents.inspectOwned("kb-1", "foreign-document")).thenReturn(Optional.empty());
        SchemaDraftSourceNode missing = source("source-missing", SchemaDraftSourceType.DOCUMENT);
        missing.setDocumentId("missing-document");
        SchemaDraftSourceNode foreign = source("source-foreign", SchemaDraftSourceType.DOCUMENT);
        foreign.setDocumentId("foreign-document");

        assertThatThrownBy(() -> factory.prepare("kb-1", missing))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Referenced document is unavailable");
        assertThatThrownBy(() -> factory.prepare("kb-1", foreign))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Referenced document is unavailable");

        assertThat(missing.getStatus()).isEqualTo(SchemaDraftSourceStatus.UNAVAILABLE);
        assertThat(foreign.getStatus()).isEqualTo(SchemaDraftSourceStatus.UNAVAILABLE);
        verify(documents).inspectOwned("kb-1", "missing-document");
        verify(documents).inspectOwned("kb-1", "foreign-document");
        verify(documents, never()).readOwned(anyString(), anyString());
        verify(documents, never()).parse(anyString(), anyString(), any(byte[].class));
        verifyNoInteractions(storage, runtimeSettings);
    }

    @Test
    void mismatchedDocumentFingerprintBecomesStaleBeforeBytesAreReadOrParsed() {
        SchemaDraftSourceNode source = source("source-stale", SchemaDraftSourceType.DOCUMENT);
        source.setDocumentId("document-2");
        source.setSha256("draft-sha");
        when(documents.inspectOwned("kb-1", "document-2")).thenReturn(Optional.of(
            new DraftDocumentInputs.Metadata("document-2", "source.pdf", "application/pdf", 100L, "current-sha")));

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Referenced document is stale");

        assertThat(source.getStatus()).isEqualTo(SchemaDraftSourceStatus.STALE);
        verify(documents).inspectOwned("kb-1", "document-2");
        verify(documents, never()).readOwned(anyString(), anyString());
        verify(documents, never()).parse(anyString(), anyString(), any(byte[].class));
        verifyNoInteractions(storage, runtimeSettings);
    }

    @Test
    void documentReadFailureRetainsUnavailableClassification() {
        SchemaDraftSourceNode source = source("source-read-failed", SchemaDraftSourceType.DOCUMENT);
        source.setDocumentId("document");
        when(documents.inspectOwned("kb-1", "document")).thenReturn(Optional.of(
            new DraftDocumentInputs.Metadata("document", "notes.txt", "text/plain", 1L, source.getSha256())));
        when(documents.readOwned("kb-1", "document")).thenThrow(
            new IllegalArgumentException("Document content cannot be read: document", new IOException("gone")));

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Draft source content is unavailable")
            .satisfies(exception -> assertThat(new io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureClassifier()
                .classify((RuntimeException) exception).code())
                .isEqualTo(io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureCode.SOURCE_UNAVAILABLE));
        verify(documents, never()).parse(anyString(), anyString(), any(byte[].class));
    }

    @Test
    void documentReplacementBetweenInspectionAndReadIsRejectedBeforeParsing() throws Exception {
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        String fingerprint = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(original));
        SchemaDraftSourceNode source = source("source-replaced", SchemaDraftSourceType.DOCUMENT);
        source.setDocumentId("document");
        source.setSha256(fingerprint);
        when(documents.inspectOwned("kb-1", "document")).thenReturn(Optional.of(
            new DraftDocumentInputs.Metadata("document", "notes.txt", "text/plain", original.length, fingerprint)));
        when(documents.readOwned("kb-1", "document")).thenReturn(replacement);

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .hasMessage("Referenced document is stale");
        assertThat(source.getStatus()).isEqualTo(SchemaDraftSourceStatus.STALE);
        verify(documents, never()).parse(anyString(), anyString(), any(byte[].class));
        verifyNoInteractions(runtimeSettings);
    }

    @Test
    void ownedDocumentIsReadInScopeParsedAndSplitWithStableChunkIds() {
        SchemaDraftSourceNode source = source("source-17", SchemaDraftSourceType.DOCUMENT);
        source.setDocumentId("document-9");
        source.setRevision(8L);
        source.setName("source.pdf");
        source.setContentType("application/pdf");
        byte[] documentBytes = "raw document bytes".getBytes(StandardCharsets.UTF_8);
        source.setSha256(DraftSourceFingerprint.sha256(documentBytes));
        when(documents.inspectOwned("kb-1", "document-9")).thenReturn(Optional.of(
            new DraftDocumentInputs.Metadata("document-9", "source.pdf", "application/pdf",
                documentBytes.length, source.getSha256())));
        when(documents.readOwned("kb-1", "document-9")).thenReturn(documentBytes);
        when(documents.parse("source.pdf", "application/pdf", documentBytes)).thenReturn("  abcdef  ");
        when(runtimeSettings.discovery()).thenReturn(discoverySettings(100, 3, 4));

        PreparedDiscoverySource prepared = factory.prepare("kb-1", source);

        assertThat(prepared.sourceId()).isEqualTo("source-17");
        assertThat(prepared.type()).isEqualTo(SourceType.DOCUMENT);
        assertThat(prepared.name()).isEqualTo("source.pdf");
        assertThat(prepared.documentId()).isEqualTo("document-9");
        assertThat(prepared.fingerprint()).isEqualTo(source.getSha256());
        assertThat(prepared.chunks()).extracting(PreparedDiscoverySource.AnalysisChunk::id)
            .containsExactly("source-17-r8-chunk-001", "source-17-r8-chunk-002");
        assertThat(prepared.chunks()).extracting(PreparedDiscoverySource.AnalysisChunk::text)
            .containsExactly("abc", "def");

        InOrder order = inOrder(documents, runtimeSettings);
        order.verify(documents).inspectOwned("kb-1", "document-9");
        order.verify(documents).readOwned("kb-1", "document-9");
        order.verify(documents).parse("source.pdf", "application/pdf", documentBytes);
        order.verify(runtimeSettings).discovery();
    }

    @Test
    void textSourceTrimsContentAndSkipsDocumentParsing() throws IOException {
        SchemaDraftSourceNode source = source("source-text", SchemaDraftSourceType.TEXT);
        when(storage.read(sourceUri(source))).thenReturn(new ByteArrayInputStream(
            " \t alpha beta \n ".getBytes(StandardCharsets.UTF_8)));
        when(runtimeSettings.discovery()).thenReturn(discoverySettings(100, 40, 2));

        PreparedDiscoverySource prepared = factory.prepare("kb-1", source);

        assertThat(prepared.type()).isEqualTo(SourceType.TEXT);
        assertThat(prepared.chunks()).extracting(PreparedDiscoverySource.AnalysisChunk::text)
            .containsExactly("alpha beta");
        verify(runtimeSettings).discovery();
        verifyNoInteractions(documents);
    }

    @Test
    void blankTextFailsBeforeSettingsLookupOrDocumentParsing() throws IOException {
        SchemaDraftSourceNode source = source("source-blank", SchemaDraftSourceType.TEXT);
        when(storage.read(sourceUri(source))).thenReturn(new ByteArrayInputStream(
            " \t\n ".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Draft source has no parseable content");

        verify(runtimeSettings, never()).discovery();
        verifyNoInteractions(documents);
    }

    @Test
    void parsedCharacterLimitTakesPrecedenceOverChunkLimit() throws IOException {
        SchemaDraftSourceNode source = source("source-character-limit", SchemaDraftSourceType.TEXT);
        when(storage.read(sourceUri(source))).thenReturn(new ByteArrayInputStream(
            "abcd".getBytes(StandardCharsets.UTF_8)));
        when(runtimeSettings.discovery()).thenReturn(discoverySettings(3, 2, 1));

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Draft source exceeds parsed character limit");

        verify(runtimeSettings).discovery();
        verifyNoInteractions(documents);
    }

    @Test
    void chunkLimitAppliesAfterCharacterLimitPasses() throws IOException {
        SchemaDraftSourceNode source = source("source-chunk-limit", SchemaDraftSourceType.TEXT);
        when(storage.read(sourceUri(source))).thenReturn(new ByteArrayInputStream(
            "abcd".getBytes(StandardCharsets.UTF_8)));
        when(runtimeSettings.discovery()).thenReturn(discoverySettings(10, 2, 1));

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Draft source exceeds chunk limit");

        verify(runtimeSettings).discovery();
        verifyNoInteractions(documents);
    }

    @Test
    void fileBinaryReadIOExceptionUsesUnavailableContentError() throws IOException {
        SchemaDraftSourceNode source = source("source-file", SchemaDraftSourceType.FILE);
        IOException failure = new IOException("disk unavailable");
        when(storage.read(sourceUri(source))).thenThrow(failure);

        assertThatThrownBy(() -> factory.prepare("kb-1", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Draft source content is unavailable")
            .hasCause(failure);

        verifyNoInteractions(documents);
        verify(runtimeSettings, never()).discovery();
    }

    private SchemaDraftSourceNode source(String id, SchemaDraftSourceType type) {
        SchemaDraftSourceNode source = new SchemaDraftSourceNode();
        source.setId(id);
        source.setDraftId("draft-1");
        source.setKnowledgeBaseId("kb-1");
        source.setType(type);
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        source.setRevision(8L);
        source.setDocumentId("document-1");
        source.setName("source.txt");
        source.setContentType("text/plain");
        source.setSizeBytes(10L);
        source.setSha256("draft-sha");
        source.setContentUri("file:///drafts/kb-1/draft-1/source.bin");
        return source;
    }

    private RuntimeSettingsService.DiscoverySettings discoverySettings(
        int maxSourceCharacters, int chunkCharacters, int maxChunksPerSource
    ) {
        return new RuntimeSettingsService.DiscoverySettings(
            10, 1024, 4096, maxSourceCharacters, 8192, chunkCharacters, maxChunksPerSource, 2,
            Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    private URI sourceUri(SchemaDraftSourceNode source) {
        return URI.create(source.getContentUri());
    }
}
