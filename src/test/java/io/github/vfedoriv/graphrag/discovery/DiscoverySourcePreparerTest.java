package io.github.vfedoriv.graphrag.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.DiscoveryGuidance;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.TextSource;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoverySourcePreparerTest {

    private final DocumentUploadRepository repository = mock(DocumentUploadRepository.class);
    private final DocumentUploadService uploadService = mock(DocumentUploadService.class);
    private final DocumentParsingService parsingService = mock(DocumentParsingService.class);
    private final KnowledgeBaseLifecycleService lifecycleService = mock(KnowledgeBaseLifecycleService.class);
    private final RuntimeSettingsService settingsService = mock(RuntimeSettingsService.class);
    private DiscoverySourcePreparer preparer;

    @BeforeEach
    void setUp() {
        when(settingsService.discovery()).thenReturn(new RuntimeSettingsService.DiscoverySettings(
            3, 100, 200, 100, 200, 4, 3, 2, Duration.ofSeconds(1), Duration.ofSeconds(2)));
        preparer = new DiscoverySourcePreparer(repository, uploadService, parsingService, lifecycleService, settingsService);
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
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("document");
        document.setKnowledgeBaseId("other-kb");
        when(repository.findById("document")).thenReturn(Optional.of(document));
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
}
