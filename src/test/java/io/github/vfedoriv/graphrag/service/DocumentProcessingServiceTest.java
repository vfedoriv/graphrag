package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
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
    private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;

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
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties));
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
        when(knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(profile(appProperties));
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
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties));
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
        when(knowledgeBaseService.activeAiProfile(doc.getKnowledgeBaseId())).thenReturn(profile(appProperties));
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

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 2, 10),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private DocumentProcessingService service(ChunkingService chunkingService) {
        ObjectMapper objectMapper = new ObjectMapper();
        ProfileScopedAiClientResolver clientResolver = new ProfileScopedAiClientResolver(
            embeddingClientProvider,
            new EmptyObjectProvider<>(),
            new EmptyObjectProvider<>()
        );
        DocumentChunkPersistenceAdapter persistenceAdapter = new DocumentChunkPersistenceAdapter(
            documentChunkRepository,
            new EmbeddingSpaceIndexService(neo4jClient),
            neo4jClient
        );
        return new DocumentProcessingService(
            documentUploadRepository,
            documentChunkRepository,
            extractionRunRepository,
            documentUploadService,
            documentParsingService,
            chunkingService,
            new DocumentProcessingOptionsRegistry(),
            clientResolver,
            objectMapper,
            graphExtractionService,
            TestAiObservationService.noop(),
            knowledgeBaseService,
            knowledgeBaseLifecycleService,
            new EmbeddingSpacePolicy(documentChunkRepository),
            new io.github.vfedoriv.graphrag.application.processing.ProcessingRunLifecycle(
                documentProcessingRunRepository,
                objectMapper
            ),
            persistenceAdapter
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
