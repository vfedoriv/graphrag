package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;

import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;

import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftJsonSupport;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.ChunkMigrationSnapshot;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionSet;

import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing.ReprocessingDocumentPreparationAdapter;
import io.github.vfedoriv.graphrag.domain.*;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentPreparation;
import io.github.vfedoriv.graphrag.service.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentMigrationPreparationFacadeTest {
    private final DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
    private final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    private final DocumentProcessingRunRepository runs = mock(DocumentProcessingRunRepository.class);
    private final RuntimeSettingsService runtime = mock(RuntimeSettingsService.class);
    private final ChunkingService chunking = new ChunkingService(runtime);
    private final EmbeddingCompatibility embedding = io.github.vfedoriv.graphrag.support.AiBoundaryTestSupport.compatibility(chunks);
    private final DocumentMigrationPreparationFacade facade = new DocumentMigrationPreparationFacade(
        documents, chunks, runs, new DocumentProcessingOptionsRegistry(), chunking, embedding, new ObjectMapper());
    private final ReprocessingDocumentPreparationAdapter adapter = new ReprocessingDocumentPreparationAdapter(facade);
    private final ReprocessingDocumentPreparation.Profile captured = new ReprocessingDocumentPreparation.Profile(
        "profile", 7, "https://example.test/v1", "unknown-local-model", 3, null);

    DocumentMigrationPreparationFacadeTest() {
        when(runtime.chunking()).thenReturn(new RuntimeSettingsAccess.ChunkingSettings(
            "recursive", 800, 80, 4000, 1600, 8000, 2, 64, 256, "representation-v1"));
        when(runtime.effectiveChunkerRevision()).thenReturn("target-revision");
    }

    @Test
    void selectionPreservesOwnershipOrderDeduplicationAndCapturedHashes() {
        DocumentUploadNode a = document("a");
        DocumentUploadNode b = document("b");
        when(documents.findByKnowledgeBaseIdOrderByUploadedAtDesc("kb")).thenReturn(List.of(b, a));
        when(documents.findByIdAndKnowledgeBaseId("a", "kb")).thenReturn(Optional.of(a));
        when(documents.findByIdAndKnowledgeBaseId("b", "kb")).thenReturn(Optional.of(b));
        assertThat(adapter.allOwned("kb")).extracting(ReprocessingDocumentPreparation.Summary::id)
            .containsExactly("b", "a");
        List<ReprocessingDocumentPreparation.Summary> selected = adapter.ownedIds("kb", List.of("a", "b", "a"));
        a.setSha256("replaced");
        assertThat(selected).extracting(ReprocessingDocumentPreparation.Summary::id).containsExactly("a", "b");
        assertThat(selected.getFirst().sha256()).isEqualTo("hash-a");
        assertThatThrownBy(selected::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.ownedIds("kb", List.of("foreign"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void currentRequiresChunksAndActiveCompletedMatchingSourceAndRevision() {
        DocumentUploadNode source = document("a");
        when(documents.findByKnowledgeBaseIdOrderByUploadedAtDesc("kb")).thenReturn(List.of(source));
        List<ReprocessingDocumentPreparation.Summary> selected = adapter.allOwned("kb");
        when(chunks.findByDocumentIdOrderByChunkIndexAsc("a")).thenReturn(List.of());
        assertThat(adapter.prepare(selected, captured, Map.of()).getFirst().classification())
            .isEqualTo(ReprocessingDocumentPreparation.Classification.NO_CHUNKS);
        when(chunks.findByDocumentIdOrderByChunkIndexAsc("a")).thenReturn(List.of(new DocumentChunkNode()));
        String revision = chunking.snapshot(profile().facts(), "text").effectiveRevision().value();
        for (int condition = 0; condition < 5; condition++) {
            DocumentProcessingRunNode run = new DocumentProcessingRunNode();
            run.setActiveCompleted(condition != 1);
            run.setStatus(condition == 2 ? DocumentProcessingRunStatus.FAILED : DocumentProcessingRunStatus.COMPLETED);
            run.setSourceSha256(condition == 3 ? "stale" : "hash-a");
            run.setEffectiveChunkerRevision(condition == 4 ? "old-revision" : revision);
            when(runs.findByDocumentIdOrderByStartedAtAsc("a")).thenReturn(List.of(run));
            assertThat(adapter.prepare(selected, captured, Map.of()).getFirst().classification())
                .isEqualTo(condition == 0 ? ReprocessingDocumentPreparation.Classification.CURRENT
                    : ReprocessingDocumentPreparation.Classification.OUTDATED);
        }
    }

    @Test
    void mappedSnapshotsPreserveCanonicalFieldsFingerprintAndExecutionRestoration() {
        DocumentUploadNode source = document("a");
        source.setProcessingDefaultsJson("{\"preserveLineBreaks\":false}");
        when(documents.findByKnowledgeBaseIdOrderByUploadedAtDesc("kb")).thenReturn(List.of(source));
        ReprocessingDocumentPreparation.Inspection inspection = adapter.inspect("kb", captured);
        ReprocessingDocumentPreparation.Prepared prepared = adapter.prepare(adapter.allOwned("kb"), captured,
            Map.of("preserveLineBreaks", true)).getFirst();
        ObjectMapper mapper = new ObjectMapper();
        SchemaDraftJsonSupport json = new SchemaDraftJsonSupport(mapper);
        ChunkMigrationSnapshot.ChunkTarget chunk = mapper.convertValue(inspection.chunkTarget(), ChunkMigrationSnapshot.ChunkTarget.class);
        ChunkMigrationSnapshot.DocumentTarget target = mapper.convertValue(prepared.target(), ChunkMigrationSnapshot.DocumentTarget.class);
        DocumentProcessingOptionSet legacyOptions = new io.github.vfedoriv.graphrag.documents.application.processing.ProcessingOptionResolver(
            new DocumentProcessingOptionsRegistry(), new io.github.vfedoriv.graphrag.documents.application.processing.ProcessingJsonCodec(mapper))
            .resolve(source, Map.of("preserveLineBreaks", true));
        io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext legacy = chunking.snapshot(profile().facts(), "text");
        ChunkMigrationSnapshot.DocumentTarget expected = new ChunkMigrationSnapshot.DocumentTarget(
            "hash-a", "text", legacy.parserRevision(), "TXT", legacy.effectiveRevision().value(), legacyOptions.effectiveOptions());
        ChunkMigrationSnapshot before = new ChunkMigrationSnapshot("target-revision", ChunkReprocessingSelection.ALL,
            mapper.convertValue(chunking.snapshotTarget(profile().facts()), ChunkMigrationSnapshot.ChunkTarget.class), "profile", 7, io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget.derive(profile().getBaseUrl(), profile().getEmbeddingModel(),
            profile().getEmbeddingDimensions(), profile().getTokenizerId() == null ? null : profile().getTokenizerId().value()).id(), "schema", "schema-hash", Map.of("a", expected));
        ChunkMigrationSnapshot after = new ChunkMigrationSnapshot(inspection.targetRevision(), ChunkReprocessingSelection.ALL,
            chunk, captured.id(), captured.revision(), inspection.embeddingSpaceId(), "schema", "schema-hash", Map.of("a", target));
        assertThat(json.canonical(after)).isEqualTo(json.canonical(before));
        assertThat(json.fingerprint(json.canonical(after))).isEqualTo(json.fingerprint(json.canonical(before)));
        assertThat(chunking.restore(profile().facts(), mapper.convertValue(chunk, DocumentReprocessing.ChunkTarget.class), mapper.convertValue(target, DocumentReprocessing.DocumentTarget.class))).usingRecursiveComparison()
            .ignoringFields("tokenEstimator").isEqualTo(legacy);
        assertThat(chunking.restore(profile().facts(), mapper.convertValue(chunk, DocumentReprocessing.ChunkTarget.class), mapper.convertValue(target, DocumentReprocessing.DocumentTarget.class)).tokenEstimator().revision())
            .isEqualTo(legacy.tokenEstimator().revision());
        assertThat(target.effectiveProcessingOptions()).containsEntry("preserveLineBreaks", true);
    }

    @Test
    void inspectionRetainsPartialTargetCompatibilityBlockerAndLiveIdentity() {
        DocumentChunkNode incompatible = new DocumentChunkNode();
        incompatible.setId("chunk");
        incompatible.setEmbeddingSpaceId("foreign-space");
        incompatible.setTokenizerId("utf8-byte-v1");
        when(chunks.findEmbeddedChunksByKnowledgeBaseId("kb")).thenReturn(List.of(incompatible));
        ReprocessingDocumentPreparation.Inspection inspection = adapter.inspect("kb", captured);
        assertThat(inspection.blocker().code()).isEqualTo("EMBEDDING_SPACE_INCOMPATIBLE");
        assertThat(inspection.targetRevision()).isEqualTo("target-revision");
        assertThat(inspection.embeddingSpaceId()).isEqualTo(io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget.derive(profile().getBaseUrl(), profile().getEmbeddingModel(),
            profile().getEmbeddingDimensions(), profile().getTokenizerId() == null ? null : profile().getTokenizerId().value()).id());
        assertThat(inspection.chunkTarget()).isNull();
        when(runtime.effectiveChunkerRevision()).thenReturn("changed");
        assertThat(adapter.identity(captured).targetRevision()).isEqualTo("changed");
        ReprocessingDocumentPreparation.Profile changed = new ReprocessingDocumentPreparation.Profile(
            captured.id(), 8, captured.baseUrl(), "changed-model", 4, null);
        assertThat(adapter.identity(changed).embeddingSpaceId()).isNotEqualTo(adapter.identity(captured).embeddingSpaceId());
    }

    private DocumentUploadNode document(String id) {
        DocumentUploadNode source = new DocumentUploadNode();
        source.setId(id);
        source.setKnowledgeBaseId("kb");
        source.setOriginalFilename(id + ".txt");
        source.setContentType("text/plain");
        source.setSha256("hash-" + id);
        source.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        source.setStatus(DocumentStatus.COMPLETED);
        return source;
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(captured.id());
        profile.setRevision(captured.revision());
        profile.setBaseUrl(captured.baseUrl());
        profile.setEmbeddingModel(captured.embeddingModel());
        profile.setEmbeddingDimensions(captured.embeddingDimensions());
        return profile;
    }
}
