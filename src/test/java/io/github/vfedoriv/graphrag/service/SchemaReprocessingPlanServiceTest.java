package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.document.chunking.Utf8ByteTokenEstimator;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.CreatePlanRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationPreviewRequest;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.RetryMode;
import io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.RetryPlanRequest;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

class SchemaReprocessingPlanServiceTest {

    @Test
    void selectsOwnedOutdatedDocumentsAndPersistsTheirImmutableHashes() {
        Fixture fixture = fixture();

        fixture.service.create("kb-1", chunkRequest(ChunkReprocessingSelection.OUTDATED_STRATEGY, List.of()));

        ArgumentCaptor<SchemaReprocessingPlanNode> planCaptor =
            ArgumentCaptor.forClass(SchemaReprocessingPlanNode.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaReprocessingItemNode>> itemCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(fixture.checkpoint).createPlan(planCaptor.capture(), itemCaptor.capture());
        SchemaReprocessingPlanNode plan = planCaptor.getValue();
        ChunkMigrationSnapshot snapshot = fixture.jsonSupport.read(
            plan.getTargetSnapshotJson(),
            ChunkMigrationSnapshot.class
        );
        assertThat(plan.getReason()).isEqualTo(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        assertThat(plan.getSelection()).isEqualTo(ChunkReprocessingSelection.OUTDATED_STRATEGY);
        assertThat(snapshot.documents()).containsOnlyKeys("doc-1");
        assertThat(snapshot.documents().get("doc-1").sourceSha256()).isEqualTo("a".repeat(64));
        assertThat(itemCaptor.getValue()).singleElement()
            .extracting(SchemaReprocessingItemNode::getDocumentSha256)
            .isEqualTo("a".repeat(64));
    }

    @Test
    void selectsAllDocumentsOnlyWhenSchemaActivationRequestsIt() {
        Fixture fixture = schemaFixture();

        fixture.service.create("kb-1", schemaRequest(true, List.of()));

        verify(fixture.documents).findByKnowledgeBaseIdOrderByUploadedAtDesc("kb-1");
    }

    @Test
    void selectsExplicitDocumentsWhenSchemaActivationAllDocumentsIsOmittedNullOrFalse() {
        for (Boolean allDocuments : new Boolean[] {null, false}) {
            Fixture fixture = schemaFixture();

            fixture.service.create("kb-1", schemaRequest(allDocuments, List.of("doc-1")));

            verify(fixture.documents).findByIdAndKnowledgeBaseId("doc-1", "kb-1");
        }
    }

    @Test
    void rejectsSchemaActivationWithoutOrWithCombinedDocumentChoices() {
        Fixture withoutChoice = schemaFixture();
        assertThatThrownBy(() -> withoutChoice.service.create("kb-1", schemaRequest(false, List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("either allDocuments");

        Fixture combinedChoices = schemaFixture();
        assertThatThrownBy(() -> combinedChoices.service.create(
            "kb-1", schemaRequest(true, List.of("doc-1"))
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("either allDocuments");
    }

    @Test
    void chunkMigrationUsesSelectionInsteadOfSchemaAllDocumentsFlag() {
        for (Boolean allDocuments : new Boolean[] {null, false, true}) {
            Fixture fixture = fixture();

            fixture.service.create(
                "kb-1",
                chunkRequest(allDocuments, ChunkReprocessingSelection.DOCUMENT_IDS, List.of("doc-1"))
            );

            ArgumentCaptor<List<SchemaReprocessingItemNode>> itemCaptor = ArgumentCaptor.forClass(List.class);
            verify(fixture.checkpoint).createPlan(any(), itemCaptor.capture());
            assertThat(itemCaptor.getValue()).singleElement()
                .extracting(SchemaReprocessingItemNode::getDocumentId)
                .isEqualTo("doc-1");
            verify(fixture.documents).findByIdAndKnowledgeBaseId("doc-1", "kb-1");
        }
    }

    @Test
    void previewsClassificationCountsWithoutCreatingAPlan() {
        Fixture fixture = fixture();

        io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationPreviewResponse response = fixture.service.preview(
            "kb-1",
            new ChunkMigrationPreviewRequest(ChunkReprocessingSelection.OUTDATED_STRATEGY, List.of(), Map.of()),
            0,
            20
        );

        assertThat(response.ready()).isTrue();
        assertThat(response.classificationCounts().noChunks()).isEqualTo(1);
        assertThat(response.classificationCounts().outdated()).isEqualTo(0);
        assertThat(response.selectedCount()).isEqualTo(1);
        assertThat(response.selectedDocuments().getContent()).singleElement()
            .extracting(value -> value.id()).isEqualTo("doc-1");
        verify(fixture.checkpoint, never()).createPlan(any(), any());
    }

    @Test
    void previewReportsActivePlanBlockerWithoutCreatingWork() {
        Fixture fixture = fixture();
        when(fixture.plans.existsActiveByKnowledgeBaseId("kb-1")).thenReturn(true);

        io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationPreviewResponse response = fixture.service.preview(
            "kb-1",
            new ChunkMigrationPreviewRequest(ChunkReprocessingSelection.ALL, List.of(), Map.of()),
            0,
            20
        );

        assertThat(response.ready()).isFalse();
        assertThat(response.blockers()).extracting(value -> value.code())
            .contains("ACTIVE_DESTRUCTIVE_PLAN");
        verify(fixture.checkpoint, never()).createPlan(any(), any());
    }

    @Test
    void previewPreservesOwnershipSafetyAndBoundedEmptyPages() {
        Fixture fixture = fixture();
        when(fixture.documents.findByIdAndKnowledgeBaseId("foreign", "kb-1"))
            .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> fixture.service.preview(
            "kb-1",
            new ChunkMigrationPreviewRequest(ChunkReprocessingSelection.DOCUMENT_IDS, List.of("foreign"), Map.of()),
            0,
            20
        )).isInstanceOf(io.github.vfedoriv.graphrag.error.NotFoundException.class);

        io.github.vfedoriv.graphrag.dto.SchemaReprocessingDtos.ChunkMigrationPreviewResponse response =
            fixture.service.preview(
                "kb-1",
                new ChunkMigrationPreviewRequest(ChunkReprocessingSelection.ALL, List.of(), Map.of()),
                1,
                1
            );
        assertThat(response.selectedCount()).isEqualTo(1);
        assertThat(response.selectedDocuments().getContent()).isEmpty();
        assertThat(response.selectedDocuments().getTotalElements()).isEqualTo(1);
    }

    @Test
    void retryRejectsConflictingModeAndLegacyBoolean() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.retry(
            "kb-1", "plan-1", new RetryPlanRequest(RetryMode.RESNAPSHOT_UNRESOLVED, true)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("conflicts");
        assertThatThrownBy(() -> fixture.service.retry("kb-1", "plan-1", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("explicit unresolved-document");
    }

    @Test
    void rejectsAStaleExpectedRevisionBeforeCreatingItems() {
        Fixture fixture = fixture();
        CreatePlanRequest request = new CreatePlanRequest(
            null,
            null,
            false,
            List.of(),
            Map.of(),
            ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION,
            ChunkReprocessingSelection.ALL,
            "stale-revision"
        );

        assertThatThrownBy(() -> fixture.service.create("kb-1", request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("current revision");
        verify(fixture.checkpoint, never()).createPlan(any(), any());
    }

    @Test
    void retryResnapshotsOnlyUnresolvedDocumentsAndLinksPriorItems() {
        Fixture fixture = fixture();
        SchemaReprocessingPlanNode prior = new SchemaReprocessingPlanNode();
        prior.setId("plan-prior");
        prior.setKnowledgeBaseId("kb-1");
        prior.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        prior.setStatus(SchemaReprocessingPlanStatus.PARTIAL);
        SchemaReprocessingItemNode succeeded = item("item-succeeded", "doc-succeeded");
        succeeded.setStatus(SchemaReprocessingItemStatus.SUCCEEDED);
        SchemaReprocessingItemNode stale = item("item-stale", "doc-1");
        stale.setStatus(SchemaReprocessingItemStatus.STALE_SOURCE);
        when(fixture.plans.findByIdAndKnowledgeBaseId("plan-prior", "kb-1"))
            .thenReturn(Optional.of(prior));
        when(fixture.items.findByPlanIdOrderByDocumentIdAsc("plan-prior"))
            .thenReturn(List.of(succeeded, stale));
        when(fixture.documents.findByIdAndKnowledgeBaseId("doc-1", "kb-1"))
            .thenReturn(Optional.of(fixture.document));

        fixture.service.retry("kb-1", "plan-prior", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaReprocessingItemNode>> itemCaptor =
            ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<SchemaReprocessingPlanNode> planCaptor =
            ArgumentCaptor.forClass(SchemaReprocessingPlanNode.class);
        verify(fixture.checkpoint).createPlan(planCaptor.capture(), itemCaptor.capture());
        assertThat(planCaptor.getValue().getRetryOfPlanId()).isEqualTo("plan-prior");
        assertThat(itemCaptor.getValue()).singleElement()
            .satisfies(item -> {
                assertThat(item.getDocumentId()).isEqualTo("doc-1");
                assertThat(item.getPriorItemId()).isEqualTo("item-stale");
                assertThat(item.getDocumentSha256()).isEqualTo("a".repeat(64));
            });
    }

    @Test
    void marksAChangedSourceStaleWithoutInvokingOverwriteProcessing() {
        Fixture fixture = fixture();
        SchemaReprocessingPlanNode plan = queuedChunkPlan();
        SchemaReprocessingItemNode item = item("item-1", "doc-1");
        item.setPlanId(plan.getId());
        item.setStatus(SchemaReprocessingItemStatus.QUEUED);
        DocumentUploadNode replacement = document();
        replacement.setSha256("f".repeat(64));
        when(fixture.plans.claim(eq(plan.getId()), anyString(), any(), any())).thenReturn(1L);
        when(fixture.plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(fixture.items.findByPlanIdOrderByDocumentIdAsc(plan.getId())).thenReturn(List.of(item));
        when(fixture.items.claim(eq(item.getId()), anyString(), any(), any())).thenReturn(1L);
        when(fixture.items.findById(item.getId())).thenReturn(Optional.of(item));
        when(fixture.documents.findByIdAndKnowledgeBaseId("doc-1", "kb-1"))
            .thenReturn(Optional.of(replacement));
        when(fixture.items.complete(
            eq(item.getId()),
            anyString(),
            eq(SchemaReprocessingItemStatus.STALE_SOURCE),
            eq("SOURCE_CHANGED"),
            eq(false),
            any()
        )).thenAnswer(invocation -> {
            item.setStatus(SchemaReprocessingItemStatus.STALE_SOURCE);
            return 1L;
        });
        when(fixture.plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.execute(plan.getId());

        verify(fixture.processing, never()).process(
            eq("doc-1"),
            eq(true),
            any(ImmutableDocumentProcessingInput.class)
        );
        verify(fixture.items).complete(
            eq(item.getId()),
            anyString(),
            eq(SchemaReprocessingItemStatus.STALE_SOURCE),
            eq("SOURCE_CHANGED"),
            eq(false),
            any()
        );
    }

    @Test
    void blocksQueuedItemsWhenTheSnapshottedTargetChanges() {
        Fixture fixture = fixture();
        SchemaReprocessingPlanNode plan = queuedChunkPlan();
        SchemaReprocessingItemNode item = item("item-1", "doc-1");
        item.setPlanId(plan.getId());
        item.setStatus(SchemaReprocessingItemStatus.QUEUED);
        AiProfileNode changedProfile = profile();
        changedProfile.setId("profile-changed");
        when(fixture.knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(changedProfile);
        when(fixture.plans.claim(eq(plan.getId()), anyString(), any(), any())).thenReturn(1L);
        when(fixture.plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(fixture.items.findByPlanIdOrderByDocumentIdAsc(plan.getId())).thenReturn(List.of(item));
        when(fixture.items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.execute(plan.getId());

        assertThat(item.getStatus()).isEqualTo(SchemaReprocessingItemStatus.BLOCKED_TARGET_CHANGED);
        assertThat(item.getFailureCategory()).isEqualTo("TARGET_CHANGED");
        assertThat(plan.getBlockedDocuments()).isEqualTo(1);
        verify(fixture.processing, never()).process(
            eq("doc-1"),
            eq(true),
            any(ImmutableDocumentProcessingInput.class)
        );
    }

    private CreatePlanRequest chunkRequest(
        ChunkReprocessingSelection selection,
        List<String> documentIds
    ) {
        return chunkRequest(false, selection, documentIds);
    }

    private CreatePlanRequest chunkRequest(
        Boolean allDocuments,
        ChunkReprocessingSelection selection,
        List<String> documentIds
    ) {
        return new CreatePlanRequest(
            null,
            null,
            allDocuments,
            documentIds,
            Map.of(),
            ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION,
            selection,
            "chunker-current"
        );
    }

    private CreatePlanRequest schemaRequest(Boolean allDocuments, List<String> documentIds) {
        return new CreatePlanRequest(
            "draft-1",
            "schema-1",
            allDocuments,
            documentIds,
            Map.of(),
            ReprocessingPlanReason.SCHEMA_ACTIVATION,
            null,
            null
        );
    }

    private Fixture schemaFixture() {
        Fixture fixture = fixture();
        SchemaDraftPublicationNode publication = new SchemaDraftPublicationNode();
        publication.setDraftId("draft-1");
        publication.setKnowledgeBaseId("kb-1");
        publication.setSchemaId("schema-1");
        publication.setStatus(SchemaDraftPublicationStatus.COMPLETED);
        when(fixture.publications.findByDraftId("draft-1")).thenReturn(Optional.of(publication));
        return fixture;
    }

    private SchemaReprocessingItemNode item(String id, String documentId) {
        SchemaReprocessingItemNode item = new SchemaReprocessingItemNode();
        item.setId(id);
        item.setDocumentId(documentId);
        item.setDocumentSha256("old");
        return item;
    }

    private SchemaReprocessingPlanNode queuedChunkPlan() {
        SchemaReprocessingPlanNode plan = new SchemaReprocessingPlanNode();
        plan.setId("plan-1");
        plan.setKnowledgeBaseId("kb-1");
        plan.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        plan.setSchemaId("schema-1");
        plan.setSchemaContentHash("b".repeat(64));
        plan.setAiProfileId("profile-1");
        plan.setAiProfileRevision(3);
        plan.setEmbeddingSpaceId("es-1");
        plan.setExpectedChunkerRevision("chunker-current");
        plan.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        plan.setTotalDocuments(1);
        plan.setQueuedDocuments(1);
        plan.setCreatedAt(Instant.now());
        return plan;
    }

    private Fixture fixture() {
        KnowledgeBaseLifecycleService lifecycle = mock(KnowledgeBaseLifecycleService.class);
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        DocumentUploadRepository documents = mock(DocumentUploadRepository.class);
        DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
        DocumentProcessingRunRepository runs = mock(DocumentProcessingRunRepository.class);
        SchemaDefinitionRepository schemas = mock(SchemaDefinitionRepository.class);
        SchemaDraftPublicationRepository publications = mock(SchemaDraftPublicationRepository.class);
        SchemaReprocessingPlanRepository plans = mock(SchemaReprocessingPlanRepository.class);
        SchemaReprocessingItemRepository items = mock(SchemaReprocessingItemRepository.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        DocumentProcessingService processing = mock(DocumentProcessingService.class);
        ChunkingService chunking = mock(ChunkingService.class);
        EmbeddingSpacePolicy embeddingPolicy = mock(EmbeddingSpacePolicy.class);
        SchemaDraftWorkflowCheckpointService checkpoint = mock(SchemaDraftWorkflowCheckpointService.class);
        AiObservationService observation = mock(AiObservationService.class);
        AiObservationScope observationScope = mock(AiObservationScope.class);
        SchemaDraftJsonSupport jsonSupport = new SchemaDraftJsonSupport(new ObjectMapper());
        AiProfileNode profile = profile();
        KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
        knowledgeBase.setId("kb-1");
        knowledgeBase.setActiveSchemaId("schema-1");
        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-1");
        schema.setContentHash("b".repeat(64));
        DocumentUploadNode document = document();
        ChunkingContext context = context();
        ChunkMigrationSnapshot.ChunkTarget chunkTarget = new ChunkMigrationSnapshot.ChunkTarget(
            context.strategyName(),
            context.strategyRevision(),
            context.targetTokens(),
            context.overlapTokens(),
            context.hardCharacterLimit(),
            context.parentTargetTokens(),
            context.parentHardCharacterLimit(),
            context.parentMaxPages(),
            context.contextHeaderMaxTokens(),
            context.contextHeaderMaxCharacters(),
            context.tokenEstimator().tokenizerId().value(),
            context.tokenEstimator().revision(),
            context.tokenEstimator().countMode().name(),
            context.representationRevision(),
            context.settingsHash().value()
        );
        when(knowledgeBases.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(schemas.findById("schema-1")).thenReturn(Optional.of(schema));
        when(knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(profile);
        when(chunking.migrationTargetRevision(profile)).thenReturn("chunker-current");
        when(chunking.snapshotTarget(profile)).thenReturn(chunkTarget);
        when(chunking.snapshot(profile, "text")).thenReturn(context);
        when(embeddingPolicy.spaceFor(profile)).thenReturn(
            new EmbeddingSpace("es-1", "https://example.test", "embedding", 3, "utf8-byte-v1")
        );
        when(documents.findByKnowledgeBaseIdOrderByUploadedAtDesc("kb-1")).thenReturn(List.of(document));
        when(documents.findByIdAndKnowledgeBaseId("doc-1", "kb-1")).thenReturn(Optional.of(document));
        when(chunks.findByDocumentIdOrderByChunkIndexAsc("doc-1")).thenReturn(List.of());
        when(runs.findByDocumentIdOrderByStartedAtAsc(anyString())).thenReturn(List.of());
        when(checkpoint.createPlan(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(observation.startWorkflow(any())).thenReturn(observationScope);
        SchemaReprocessingPlanService service = new SchemaReprocessingPlanService(
            lifecycle,
            knowledgeBases,
            documents,
            chunks,
            runs,
            schemas,
            publications,
            plans,
            items,
            knowledgeBaseService,
            processing,
            new DocumentProcessingOptionsRegistry(),
            chunking,
            embeddingPolicy,
            jsonSupport,
            new ObjectMapper(),
            observation,
            mock(SchemaDraftLifecycleService.class),
            mock(SchemaDraftWorkflowNavigationService.class),
            checkpoint,
            mock(TaskExecutor.class)
        );
        return new Fixture(
            service,
            plans,
            items,
            documents,
            publications,
            knowledgeBaseService,
            processing,
            checkpoint,
            jsonSupport,
            document
        );
    }

    private DocumentUploadNode document() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        document.setOriginalFilename("document.txt");
        document.setContentType("text/plain");
        document.setSha256("a".repeat(64));
        document.setStatus(DocumentStatus.COMPLETED);
        document.setUploadedAt(Instant.now());
        return document;
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setRevision(3);
        return profile;
    }

    private ChunkingContext context() {
        return ChunkingContext.create(
            "recursive",
            "recursive-v1",
            800,
            80,
            4000,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "representation-v1"
        );
    }

    private record Fixture(
        SchemaReprocessingPlanService service,
        SchemaReprocessingPlanRepository plans,
        SchemaReprocessingItemRepository items,
        DocumentUploadRepository documents,
        SchemaDraftPublicationRepository publications,
        KnowledgeBaseService knowledgeBaseService,
        DocumentProcessingService processing,
        SchemaDraftWorkflowCheckpointService checkpoint,
        SchemaDraftJsonSupport jsonSupport,
        DocumentUploadNode document
    ) {
    }
}
