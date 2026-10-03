package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourcePreparer;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryRequest.DiscoveryGuidance;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryRequest.TextSource;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryDocumentInputs;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryFileParsing;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryKnowledgeBaseAdmission;
import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DiscoverySourcePreparerTest {

    private final DiscoveryDocumentInputs documentInputs = mock(DiscoveryDocumentInputs.class);
    private final DiscoveryFileParsing fileParsing = mock(DiscoveryFileParsing.class);
    private final DiscoveryKnowledgeBaseAdmission knowledgeBaseAdmission = mock(DiscoveryKnowledgeBaseAdmission.class);
    private final RuntimeSettingsService settingsService = mock(RuntimeSettingsService.class);
    private DiscoverySourcePreparer preparer;

    @BeforeEach
    void setUp() {
        when(settingsService.discovery()).thenReturn(new RuntimeSettingsAccess.DiscoverySettings(
            3, 100, 200, 100, 200, 4, 3, 2, Duration.ofSeconds(1), Duration.ofSeconds(2)));
        preparer = new DiscoverySourcePreparer(documentInputs, fileParsing, knowledgeBaseAdmission, settingsService);
    }

    @Test
    void createsStableSourceChunkIdentifiersAndFingerprints() {
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of(), List.of(new TextSource("sample", "abcdefgh")), null,
            DiscoveryGuidance.empty());

        List<PreparedDiscoverySource> first = preparer.prepare("kb", request, List.of());
        List<PreparedDiscoverySource> second = preparer.prepare("kb", request, List.of());

        assertThat(first).isEqualTo(second);
        assertThat(first.getFirst().sourceId()).startsWith("source-001-text-");
        assertThat(first.getFirst().chunks()).extracting(PreparedDiscoverySource.AnalysisChunk::id)
            .containsExactly(first.getFirst().sourceId() + "-chunk-001", first.getFirst().sourceId() + "-chunk-002");
    }

    @Test
    void hidesCrossKnowledgeBaseDocumentExistence() {
        when(documentInputs.readOwned("kb", "document"))
            .thenThrow(new NotFoundException("Document not found in knowledge base: document"));
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of("document"), List.of(), null, DiscoveryGuidance.empty());

        assertThatThrownBy(() -> preparer.prepare("kb", request, List.of()))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Document not found in knowledge base: document");
    }

    @Test
    void rejectsEmptyContentAndLimitsBeforeAnalysis() {
        SchemaDiscoveryRequest empty = new SchemaDiscoveryRequest(List.of(), List.of(new TextSource("empty", "   ")), null,
            DiscoveryGuidance.empty());
        SchemaDiscoveryRequest tooMany = new SchemaDiscoveryRequest(List.of(), List.of(
            new TextSource("one", "1"), new TextSource("two", "2"), new TextSource("three", "3"), new TextSource("four", "4")),
            null, DiscoveryGuidance.empty());

        assertThatThrownBy(() -> preparer.prepare("kb", empty, List.of())).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no parseable content");
        assertThatThrownBy(() -> preparer.prepare("kb", tooMany, List.of())).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source count exceeds limit");
    }

    @Test
    void preparesMixedSourcesInDocumentTextFileOrder() {
        byte[] documentBytes = "doc".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(documentInputs.readOwned("kb", "document"))
            .thenReturn(new DiscoveryDocumentInputs.Source("document", "doc.txt", "text/plain", documentBytes));
        when(fileParsing.parse("doc.txt", "text/plain", documentBytes)).thenReturn("document");
        byte[] fileBytes = "file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(fileParsing.parse("file.txt", "text/plain", fileBytes)).thenReturn("file");
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(
            List.of("document"), List.of(new TextSource("typed", "text")), null, DiscoveryGuidance.empty());
        MockMultipartFile file = new MockMultipartFile("files", "file.txt", "text/plain", fileBytes);

        List<PreparedDiscoverySource> sources = preparer.prepare("kb", request, List.of(file));

        assertThat(sources).extracting(PreparedDiscoverySource::type).containsExactly(
            DiscoveryContracts.SourceType.DOCUMENT,
            DiscoveryContracts.SourceType.TEXT,
            DiscoveryContracts.SourceType.FILE);
        assertThat(sources).extracting(PreparedDiscoverySource::sourceId)
            .allMatch(id -> id.startsWith("source-"));
    }

    @Test
    void rejectsDocumentByteLimitBeforeParserInvocation() {
        byte[] oversized = new byte[101];
        when(documentInputs.readOwned("kb", "document"))
            .thenReturn(new DiscoveryDocumentInputs.Source("document", "doc.txt", "text/plain", oversized));
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(
            List.of("document"), List.of(), null, DiscoveryGuidance.empty());

        assertThatThrownBy(() -> preparer.prepare("kb", request, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Discovery source exceeds byte limit");
        verifyNoInteractions(fileParsing);
    }
}
