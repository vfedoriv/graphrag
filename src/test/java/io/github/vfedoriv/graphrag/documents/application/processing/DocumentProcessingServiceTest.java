package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;
import io.github.vfedoriv.graphrag.indexes.adapters.graph.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.ManagedKnowledgeBases;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;

import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.api.model.DocumentChunkHierarchyResponse;
import io.github.vfedoriv.graphrag.documents.api.model.DocumentChunkPageResponse;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;
import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.documents.adapters.graph.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.documents.ports.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentUploadRepository documentUploadRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private ExtractionRunRepository extractionRunRepository;
    @Mock
    private DocumentProcessingRunRepository documentProcessingRunRepository;
    @Mock
    private DocumentUploadService documentUploadService;
    @Mock
    private DocumentParsingService documentParsingService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;
    @Mock
    private ObjectProvider<EmbeddingClient> embeddingClientProvider;
    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;
    @Mock
    private Environment environment;
    @Mock
    private GraphExtractionService graphExtractionService;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private ManagedKnowledgeBases knowledgeBaseLifecycleService;

    @Test
    void orchestratesParsingChunkingAndEmbedding() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        EmbeddingClient embeddingClient = texts -> List.of(
            List.of(0.1, 0.2, 0.3),
            List.of(0.4, 0.5, 0.6)
        );
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");
        doc.setProcessingDefaultsJson("{\"preserveLineBreaks\":true}");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties).facts());
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("chunk-one chunk-two".getBytes());
        when(documentParsingService.parseStructured(
            org.mockito.Mockito.eq("a.txt"),
            org.mockito.Mockito.eq("text/plain"),
            any(byte[].class),
            any()
        )).thenReturn(parsedDocument("text", "TXT", "abcdefghij01234567"));
        when(embeddingClientProvider.orderedStream()).thenReturn(Stream.of(embeddingClient));
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1"))
            .thenReturn(List.of(new DocumentChunkNode(), new DocumentChunkNode()));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        List<String> savedRunStages = new java.util.ArrayList<>();
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> {
            DocumentProcessingRunNode run = i.getArgument(0);
            savedRunStages.add(run.getStage());
            return run;
        });
        DocumentProcessingService service = service(chunkingService);
        DocumentUploadNode processed = service.process("doc-1", false, java.util.Map.of("preserveLineBreaks", false));

        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(processed.getProcessedAt()).isNotNull();
        ArgumentCaptor<DocumentChunkNode> chunkCaptor = ArgumentCaptor.forClass(DocumentChunkNode.class);
        verify(documentChunkRepository, times(2)).save(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues()).extracting(DocumentChunkNode::getChunkIndex).containsExactly(0, 1);
        assertThat(chunkCaptor.getAllValues().getFirst().getMetadata())
            .contains("\"source\":\"a.txt\"")
            .contains("\"parserId\":\"text\"")
            .contains("\"format\":\"TXT\"")
            .contains("\"processingRunId\":\"")
            .contains("\"sectionIndex\":0");
        ArgumentCaptor<DocumentProcessingRunNode> runCaptor = ArgumentCaptor.forClass(DocumentProcessingRunNode.class);
        verify(documentProcessingRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(savedRunStages)
            .contains("PARSING", "CHUNKING", "EMBEDDING", "EXTRACTING_GRAPH", "COMPLETED");
        DocumentProcessingRunNode completedRun = runCaptor.getAllValues().getLast();
        assertThat(completedRun.getStatus()).isEqualTo(DocumentProcessingRunStatus.COMPLETED);
        assertThat(completedRun.isActiveCompleted()).isTrue();
        assertThat(completedRun.getSavedDefaultsJson()).contains("\"preserveLineBreaks\":true");
        assertThat(completedRun.getRequestedOptionsJson()).contains("\"preserveLineBreaks\":false");
        assertThat(completedRun.getEffectiveOptionsJson()).contains("\"preserveLineBreaks\":false");
        assertThat(completedRun.getChunkStrategy()).isEqualTo("fixed-character");
        assertThat(completedRun.getChunkStrategyRevision()).isEqualTo("fixed-character-v1");
        assertThat(completedRun.getChunkSettingsHash()).matches("[0-9a-f]{64}");
        assertThat(completedRun.getTokenizerId()).isEqualTo("cl100k_base");
        assertThat(completedRun.getTokenCountMode()).isEqualTo("EXACT");
        assertThat(completedRun.getEffectiveChunkerRevision()).startsWith("chunker_");
        verify(documentProcessingRunRepository).deactivateOtherCompletedRuns("doc-1", completedRun.getId());
    }

    @Test
    void recordsFailedProcessingRunWithoutCreatingChunksOrExtraction() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(profile(appProperties).facts());
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("content".getBytes());
        when(documentParsingService.parseStructured(
            org.mockito.Mockito.eq("a.txt"),
            org.mockito.Mockito.eq("text/plain"),
            any(byte[].class),
            any()
        )).thenThrow(new IllegalArgumentException("parse failed"));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> i.getArgument(0));
        DocumentProcessingService service = service(chunkingService);

        DocumentUploadNode failed = service.process("doc-1");

        assertThat(failed.getStatus()).isEqualTo(DocumentStatus.FAILED);
        ArgumentCaptor<DocumentProcessingRunNode> runCaptor = ArgumentCaptor.forClass(DocumentProcessingRunNode.class);
        verify(documentProcessingRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        DocumentProcessingRunNode failedRun = runCaptor.getAllValues().getLast();
        assertThat(failedRun.getStatus()).isEqualTo(DocumentProcessingRunStatus.FAILED);
        assertThat(failedRun.isActiveCompleted()).isFalse();
        assertThat(failedRun.getErrorMessage()).isEqualTo("parse failed");
        verify(documentChunkRepository, never()).save(any());
        verify(graphExtractionService, never()).extract(any(), any(), org.mockito.Mockito.anyBoolean());
        verify(documentProcessingRunRepository, never()).deactivateOtherCompletedRuns(any(), any());
    }

    @Test
    void keepsLatestSavedEntityAcrossStatusTransitions() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        EmbeddingClient embeddingClient = texts -> List.of(
            List.of(0.1, 0.2, 0.3),
            List.of(0.4, 0.5, 0.6)
        );
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("a.txt");
        doc.setContentType("text/plain");
        doc.setContentUri("file:///tmp/a.txt");

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties).facts());
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("chunk-one chunk-two".getBytes());
        when(documentParsingService.parseStructured(
            org.mockito.Mockito.eq("a.txt"),
            org.mockito.Mockito.eq("text/plain"),
            any(byte[].class),
            any()
        )).thenReturn(parsedDocument("text", "TXT", "abcdefghij01234567"));
        when(embeddingClientProvider.orderedStream()).thenReturn(Stream.of(embeddingClient));
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1"))
            .thenReturn(List.of(new DocumentChunkNode(), new DocumentChunkNode()));

        AtomicLong persistedVersion = new AtomicLong(-1);
        when(documentUploadRepository.save(any(DocumentUploadNode.class))).thenAnswer(invocation -> {
            DocumentUploadNode in = invocation.getArgument(0);
            long expected = persistedVersion.get();
            long actual = in.getVersion() == null ? -1 : in.getVersion();
            if (actual != expected) {
                throw new OptimisticLockingFailureException(
                    "stale version: expected=%d actual=%d".formatted(expected, actual)
                );
            }
            long next = expected + 1;
            persistedVersion.set(next);
            DocumentUploadNode saved = new DocumentUploadNode();
            saved.setId(in.getId());
            ReflectionTestUtils.setField(saved, "version", next);
            saved.setKnowledgeBaseId(in.getKnowledgeBaseId());
            saved.setOriginalFilename(in.getOriginalFilename());
            saved.setContentType(in.getContentType());
            saved.setSizeBytes(in.getSizeBytes());
            saved.setSha256(in.getSha256());
            saved.setContentUri(in.getContentUri());
            saved.setStatus(in.getStatus());
            saved.setUploadedAt(in.getUploadedAt());
            saved.setProcessedAt(in.getProcessedAt());
            saved.setErrorMessage(in.getErrorMessage());
            saved.setProcessingDefaultsJson(in.getProcessingDefaultsJson());
            saved.setProcessingDefaultsUpdatedAt(in.getProcessingDefaultsUpdatedAt());
            return saved;
        });
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> i.getArgument(0));

        DocumentProcessingService service = service(chunkingService);

        DocumentUploadNode processed = service.process("doc-1");
        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(processed.getProcessedAt()).isNotNull();
    }

    @Test
    void chunksPageSectionsWithoutCrossingPageBoundariesAndPersistsPageMetadata() throws Exception {
        AppProperties appProperties = props();
        ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(appProperties));
        EmbeddingClient embeddingClient = texts -> List.of(
            List.of(0.1, 0.2, 0.3),
            List.of(0.4, 0.5, 0.6),
            List.of(0.7, 0.8, 0.9),
            List.of(1.0, 1.1, 1.2)
        );
        DocumentUploadNode doc = new DocumentUploadNode();
        doc.setId("doc-1");
        doc.setKnowledgeBaseId("kb-1");
        doc.setOriginalFilename("sample.pdf");
        doc.setContentType("application/pdf");
        doc.setContentUri("file:///tmp/sample.pdf");

        ParsedDocument parsedDocument = new ParsedDocument(
            "tika",
            "PDF",
            List.of(
                new ParsedSection(0, "abcdefghij01234567", "tika", "PDF", 1, 2, java.util.Map.of()),
                new ParsedSection(1, "klmnopqrst98765432", "tika", "PDF", 2, 2, java.util.Map.of())
            ),
            java.util.Map.of("Content-Type", "application/pdf")
        );

        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties).facts());
        when(extractionRunRepository.hasCompletedRun("doc-1")).thenReturn(false);
        when(documentUploadService.readContent(doc.getContentUri())).thenReturn("content".getBytes());
        when(documentParsingService.parseStructured(
            org.mockito.Mockito.eq("sample.pdf"),
            org.mockito.Mockito.eq("application/pdf"),
            any(byte[].class),
            any()
        )).thenReturn(parsedDocument);
        when(embeddingClientProvider.orderedStream()).thenReturn(Stream.of(embeddingClient));
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1"))
            .thenReturn(List.of(new DocumentChunkNode(), new DocumentChunkNode(), new DocumentChunkNode(), new DocumentChunkNode()));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentProcessingRunRepository.save(any(DocumentProcessingRunNode.class))).thenAnswer(i -> i.getArgument(0));
        DocumentProcessingService service = service(chunkingService);

        DocumentUploadNode processed = service.process("doc-1", false, java.util.Map.of("pdf.split-pages", true));

        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        ArgumentCaptor<DocumentChunkNode> chunkCaptor = ArgumentCaptor.forClass(DocumentChunkNode.class);
        verify(documentChunkRepository, times(4)).save(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues()).extracting(DocumentChunkNode::getChunkIndex).containsExactly(0, 1, 2, 3);
        assertThat(chunkCaptor.getAllValues().get(0).getText()).contains("abcdefghij");
        assertThat(chunkCaptor.getAllValues().get(1).getText()).contains("ij01234567");
        assertThat(chunkCaptor.getAllValues().get(2).getText()).contains("klmnopqrst");
        assertThat(chunkCaptor.getAllValues().get(3).getText()).contains("st98765432");
        assertThat(chunkCaptor.getAllValues().get(0).getMetadata())
            .contains("\"sectionIndex\":0")
            .contains("\"pageNumber\":1")
            .contains("\"pageCount\":2")
            .contains("\"parserMetadata\":{\"Content-Type\":\"application/pdf\"}");
        assertThat(chunkCaptor.getAllValues().get(0).getChunkStrategy()).isEqualTo("fixed-character");
        assertThat(chunkCaptor.getAllValues().get(0).getChunkStrategyRevision()).isEqualTo("fixed-character-v1");
        assertThat(chunkCaptor.getAllValues().get(0).getTokenizerId()).isEqualTo("cl100k_base");
        assertThat(chunkCaptor.getAllValues().get(0).getTokenCountMode()).isEqualTo("EXACT");
        assertThat(chunkCaptor.getAllValues().get(0).getEffectiveChunkerRevision()).startsWith("chunker_");
        assertThat(chunkCaptor.getAllValues().get(0).getSourceStart()).isZero();
        assertThat(chunkCaptor.getAllValues().get(2).getMetadata())
            .contains("\"sectionIndex\":1")
            .contains("\"pageNumber\":2")
            .contains("\"pageCount\":2");
    }

    @Test
    void pagesOwnedChunksWithValidatedFiltersAndDeterministicPageMetadata() {
        DocumentUploadNode document = document("doc-1");
        DocumentChunkNode chunk = chunk("chunk-1", "doc-1", "CHILD", "parent-1", "chunk text");
        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(documentChunkRepository.findPageByDocumentId(
            "doc-1", "CHILD", "parent-1", 2, PageRequest.of(1, 2)
        )).thenReturn(new PageImpl<>(List.of(chunk), PageRequest.of(1, 2), 3));

        DocumentChunkPageResponse response = service(new ChunkingService(TestRuntimeSettings.from(props())))
            .getDocumentChunkPage("doc-1", 1, 2, "child", " parent-1 ", 2);

        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getContent()).extracting("id").containsExactly("chunk-1");
        verify(documentChunkRepository).findPageByDocumentId(
            "doc-1", "CHILD", "parent-1", 2, PageRequest.of(1, 2)
        );
    }

    @Test
    void pagesFlatChunksWithCaseInsensitiveKindAndSectionFilter() {
        DocumentUploadNode document = document("doc-1");
        DocumentChunkNode firstFlatChild = chunk("chunk-1", "doc-1", "CHILD", null, "first chunk text");
        DocumentChunkNode secondFlatChild = chunk("chunk-2", "doc-1", "CHILD", null, "second chunk text");
        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(documentChunkRepository.findFlatPageByDocumentId(
            "doc-1", 3, PageRequest.of(0, 1)
        )).thenReturn(new PageImpl<>(List.of(firstFlatChild), PageRequest.of(0, 1), 2));
        when(documentChunkRepository.findFlatPageByDocumentId(
            "doc-1", 3, PageRequest.of(1, 1)
        )).thenReturn(new PageImpl<>(List.of(secondFlatChild), PageRequest.of(1, 1), 2));

        DocumentChunkPageResponse response = service(new ChunkingService(TestRuntimeSettings.from(props())))
            .getDocumentChunkPage("doc-1", 0, 1, " flat ", null, 3);

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent()).extracting("id").containsExactly("chunk-1");
        assertThat(response.getContent()).extracting("kind").containsExactly("CHILD");
        DocumentChunkPageResponse secondPage = service(new ChunkingService(TestRuntimeSettings.from(props())))
            .getDocumentChunkPage("doc-1", 1, 1, "FLAT", null, 3);

        assertThat(secondPage.getPage()).isEqualTo(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getContent()).extracting("id").containsExactly("chunk-2");
        verify(documentChunkRepository).findFlatPageByDocumentId("doc-1", 3, PageRequest.of(0, 1));
        verify(documentChunkRepository).findFlatPageByDocumentId("doc-1", 3, PageRequest.of(1, 1));
        verify(documentChunkRepository, never()).findPageByDocumentId(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidChunkPageFiltersBeforeGraphAccess() {
        DocumentProcessingService service = service(new ChunkingService(TestRuntimeSettings.from(props())));

        assertThatThrownBy(() -> service.getDocumentChunkPage("doc-1", 0, 101, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("size");
        assertThatThrownBy(() -> service.getDocumentChunkPage("doc-1", 0, 20, "PARENT", "parent-1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parentChunkId");
        assertThatThrownBy(() -> service.getDocumentChunkPage("doc-1", 0, 20, "unknown", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kind");
        assertThatThrownBy(() -> service.getDocumentChunkPage("doc-1", 0, 20, "FLAT", "parent-1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("parentChunkId cannot be used with kind=FLAT");
        verify(documentUploadRepository, never()).findById(any());
        verifyNoGraphPageRead();
    }

    @Test
    void directChunkLookupRequiresRelationalOwnershipAndReturnsUniformNotFound() {
        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(document("doc-1")));
        when(documentChunkRepository.findByIdAndDocumentId("chunk-foreign", "doc-1"))
            .thenReturn(Optional.empty());

        DocumentProcessingService service = service(new ChunkingService(TestRuntimeSettings.from(props())));

        assertThatThrownBy(() -> service.getDocumentChunk("doc-1", "chunk-foreign"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("chunk-foreign");
        verify(documentChunkRepository).findByIdAndDocumentId("chunk-foreign", "doc-1");

        when(documentUploadRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDocumentChunk("missing", "chunk-foreign"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Document not found");
        verify(documentChunkRepository, never()).findByIdAndDocumentId("chunk-foreign", "missing");
    }

    @Test
    void hierarchyPageReturnsMetadataOnlySummariesAndFlatCount() throws Exception {
        DocumentUploadNode document = document("doc-1");
        DocumentChunkNode parent = chunk("parent-1", "doc-1", "PARENT", null, "secret parent text");
        parent.setChildCount(2);
        when(documentUploadRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(documentChunkRepository.findParentPageByDocumentId("doc-1", PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(parent), PageRequest.of(0, 20), 1));
        when(documentChunkRepository.countFlatChunksByDocumentId("doc-1")).thenReturn(0L);

        DocumentChunkHierarchyResponse response = service(new ChunkingService(TestRuntimeSettings.from(props())))
            .getDocumentChunkHierarchy("doc-1", 0, 20);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getFlatChunkCount()).isZero();
        assertThat(response.getContent().getFirst().childCount()).isEqualTo(2);
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("secret parent text");
    }

    private DocumentUploadNode document(String id) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId("kb-1");
        return document;
    }

    private DocumentChunkNode chunk(String id, String documentId, String kind, String parentId, String text) {
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setKind(kind);
        chunk.setParentChunkId(parentId);
        chunk.setText(text);
        chunk.setChunkIndex(0);
        return chunk;
    }

    private void verifyNoGraphPageRead() {
        verify(documentChunkRepository, never()).findPageByDocumentId(
            any(), any(), any(), any(), any()
        );
    }

    private AppProperties props() {
        return new AppProperties(
            new Neo4jProperties("neo4j"),
            new ModelProperties("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new StorageProperties(Path.of("var/documents")),
            new ChunkingProperties(800, 2, 10),
            new QueryProperties(200, 15, true, List.of("CREATE")),
            new ExtractionProperties(40, 80, 2)
        );
    }

    private DocumentProcessingService service(ChunkingService chunkingService) {
        ObjectMapper objectMapper = new ObjectMapper();
        ProfileScopedAiClientResolver clientResolver = ProfileScopedAiClientResolver.fromProviders(
            embeddingClientProvider,
            new EmptyObjectProvider<>(),
            new EmptyObjectProvider<>()
        );
        DocumentChunkPersistenceAdapter persistenceAdapter = new DocumentChunkPersistenceAdapter(
            documentChunkRepository,
            new EmbeddingSpaceIndexService(neo4jClient),
            org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.indexes.contracts.LexicalIndexRepository.class),
            neo4jClient
        );
        DocumentProcessingOptionsRegistry registry = new DocumentProcessingOptionsRegistry();
        ProcessingJsonCodec codec = new ProcessingJsonCodec(objectMapper);
        return new DocumentProcessingService(
            documentUploadRepository, documentChunkRepository, extractionRunRepository,
            chunkingService, registry, TestAiObservationService.noop(), knowledgeBaseService,
            knowledgeBaseLifecycleService, codec, new ProcessingOptionResolver(registry, codec),
            new SourceParsingStage(documentUploadService, documentParsingService),
            new ChunkPreparationStage(chunkingService, new io.github.vfedoriv.graphrag.documents.domain.processing.ChunkMetadataFactory()),
            new ProcessingRunLifecycle(documentProcessingRunRepository, objectMapper),
            new EmbeddingPersistenceStage(clientResolver,
                io.github.vfedoriv.graphrag.support.AiBoundaryTestSupport.compatibility(documentChunkRepository),
                persistenceAdapter, chunkingService, codec),
            new GraphExtractionStage(graphExtractionService), persistenceAdapter
        );
    }

    private AiProfileNode profile(AppProperties appProperties) {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(AiProfileService.DEFAULT_PROFILE_ID);
        profile.setBaseUrl(appProperties.model().baseUrl());
        profile.setApiKey(appProperties.model().apiKey());
        profile.setChatModel(appProperties.model().chatModel());
        profile.setEmbeddingModel(appProperties.model().embeddingModel());
        profile.setEmbeddingDimensions(appProperties.model().embeddingDimensions());
        profile.setTimeoutSeconds(60);
        profile.setMaxRetries(2);
        return profile;
    }

    private ParsedDocument parsedDocument(String parserId, String format, String text) {
        return new ParsedDocument(
            parserId,
            format,
            List.of(new ParsedSection(0, text, parserId, format, null, null, java.util.Map.of())),
            java.util.Map.of()
        );
    }
}
