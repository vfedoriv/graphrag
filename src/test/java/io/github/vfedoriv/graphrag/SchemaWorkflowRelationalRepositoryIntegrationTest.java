package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DiffBaselineType;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;
import io.github.vfedoriv.graphrag.service.SchemaReprocessingRecoveryService;
import io.github.vfedoriv.graphrag.infrastructure.persistence.graph.GraphSchemaInitializer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;

@RelationalIntegrationTest
class SchemaWorkflowRelationalRepositoryIntegrationTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    @Autowired private KnowledgeBaseService knowledgeBaseService;
    @Autowired private SchemaRegistryService schemaRegistryService;
    @Autowired private SchemaDraftRepository draftRepository;
    @Autowired private SchemaDraftAnalysisRunRepository analysisRunRepository;
    @Autowired private SchemaDraftAggregateRevisionRepository aggregateRepository;
    @Autowired private SchemaDraftEvaluationRunRepository evaluationRunRepository;
    @Autowired private SchemaDraftPublicationRepository publicationRepository;
    @Autowired private SchemaReprocessingPlanRepository planRepository;
    @Autowired private SchemaReprocessingItemRepository itemRepository;
    @Autowired private DocumentUploadRepository documentRepository;
    @Autowired private DocumentProcessingRunRepository processingRunRepository;
    @Autowired private SchemaReprocessingRecoveryService recoveryService;
    @Autowired private ApplicationContext applicationContext;

    private String knowledgeBaseId;
    private SchemaDraftNode draft;
    private SchemaDraftAggregateRevisionNode aggregate;
    private AiProfileNode profile;

    @BeforeEach
    void prepare() {
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        knowledgeBaseId = "workflow-rel-" + UUID.randomUUID();
        knowledgeBaseLifecycleService.provision(knowledgeBaseId, knowledgeBaseId);
        profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        draft = draftRepository.save(draft());
        SchemaDraftAnalysisRunNode analysis = analysisRunRepository.save(analysisRun());
        aggregate = aggregateRepository.save(aggregate(analysis));
    }

    @Test
    void enforcesEvaluationIdentityOwnershipClaimsAndOptimisticVersion() {
        SchemaDraftEvaluationRunNode run = evaluationRunRepository.save(evaluationRun("evaluation-1"));
        SchemaDraftEvaluationRunNode stale = evaluationRunRepository.findById(run.getId()).orElseThrow();
        Instant claimedAt = Instant.now();

        assertThat(evaluationRunRepository.claim(
            run.getId(), "worker-a", claimedAt, claimedAt.plus(5, ChronoUnit.MINUTES))).isEqualTo(1);
        assertThat(evaluationRunRepository.claim(
            run.getId(), "worker-b", claimedAt, claimedAt.plus(5, ChronoUnit.MINUTES))).isZero();
        assertThat(evaluationRunRepository.findById(run.getId()).orElseThrow().getClaimedBy())
            .isEqualTo("worker-a");

        stale.setFailureCategory("STALE_WRITE");
        assertThatThrownBy(() -> evaluationRunRepository.save(stale))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        SchemaDraftEvaluationRunNode duplicate = evaluationRun("evaluation-duplicate");
        assertThatThrownBy(() -> evaluationRunRepository.save(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);

        SchemaDraftEvaluationRunNode crossOwned = evaluationRun("evaluation-cross-owner");
        crossOwned.setKnowledgeBaseId("missing-owner");
        crossOwned.setSnapshotFingerprint("e".repeat(64));
        assertThatThrownBy(() -> evaluationRunRepository.save(crossOwned))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesPublicationAndItemIdentityAndRepairsPlanCounters() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow\",\"version\":1,\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}",
            knowledgeBaseId);
        DocumentUploadNode document = documentRepository.save(document());

        SchemaDraftPublicationNode publication = publication("publication-1", schema);
        publicationRepository.save(publication);
        SchemaDraftPublicationNode duplicateTarget = publication("publication-2", schema);
        duplicateTarget.setDraftId("other-draft");
        assertThatThrownBy(() -> publicationRepository.save(duplicateTarget))
            .isInstanceOf(DataIntegrityViolationException.class);

        SchemaReprocessingPlanNode plan = planRepository.save(plan(schema));
        SchemaReprocessingItemNode item = itemRepository.save(item(plan, document, "item-1"));
        SchemaReprocessingItemNode duplicateItem = item(plan, document, "item-2");
        assertThatThrownBy(() -> itemRepository.save(duplicateItem))
            .isInstanceOf(DataIntegrityViolationException.class);

        Instant claimedAt = Instant.now();
        assertThat(planRepository.claim(
            plan.getId(), "worker-a", claimedAt, claimedAt.plus(5, ChronoUnit.MINUTES))).isEqualTo(1);
        assertThat(itemRepository.claim(
            item.getId(), "worker-a", claimedAt, claimedAt.plus(5, ChronoUnit.MINUTES))).isEqualTo(1);
        assertThat(itemRepository.claim(
            item.getId(), "worker-b", claimedAt, claimedAt.plus(5, ChronoUnit.MINUTES))).isZero();
        assertThat(itemRepository.complete(
            item.getId(), "worker-b", SchemaReprocessingItemStatus.SUCCEEDED,
            null, false, Instant.now())).isZero();
        assertThat(itemRepository.complete(
            item.getId(), "worker-a", SchemaReprocessingItemStatus.SUCCEEDED,
            null, false, Instant.now())).isEqualTo(1);

        SchemaReprocessingPlanNode repaired = recoveryService.repairCounters(plan.getId());
        assertThat(repaired.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.COMPLETED);
        assertThat(repaired.getQueuedDocuments()).isZero();
        assertThat(repaired.getSucceededDocuments()).isEqualTo(1);
        assertThat(repaired.getReason()).isEqualTo(ReprocessingPlanReason.SCHEMA_ACTIVATION);
    }

    @Test
    void enforcesOneActiveDestructivePlanPerKnowledgeBase() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow-lock\",\"version\":1,\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}",
            knowledgeBaseId
        );
        SchemaReprocessingPlanNode schemaPlan = plan(schema);
        planRepository.save(schemaPlan);
        DocumentUploadNode document = documentRepository.save(document());
        itemRepository.save(item(schemaPlan, document, "active-plan-item"));
        SchemaReprocessingPlanNode competing = plan(schema);
        competing.setId("plan-competing");

        assertThatThrownBy(() -> planRepository.save(competing))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void relationalContextDisablesOnlyGraphStartupInitialization() {
        assertThat(applicationContext.getBeansOfType(GraphSchemaInitializer.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(
            Neo4jTestcontainersConfiguration.class
        )).isEmpty();
        assertThat(applicationContext.getBeanProvider(
            io.github.vfedoriv.graphrag.service.QueryNeo4jExecutor.class
        ).getIfAvailable()).isNotNull();
    }

    @Test
    void repairsStoredTotalFromAuthoritativeItemCardinality() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow-total-repair\",\"version\":1,"
                + "\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}",
            knowledgeBaseId
        );
        SchemaReprocessingPlanNode plan = plan(schema);
        planRepository.save(plan);

        SchemaReprocessingPlanNode repaired = recoveryService.repairCounters(plan.getId());

        assertThat(repaired.getTotalDocuments()).isZero();
        assertThat(repaired.getQueuedDocuments()).isZero();
        assertThat(repaired.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.COMPLETED);
        assertThat(repaired.getCompletedAt()).isNotNull();
    }

    @Test
    void recoversExternalSuccessBeforeTheItemCompletionCheckpointForBothReasons() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow-external-success\",\"version\":1,"
                + "\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}", knowledgeBaseId);
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        for (ReprocessingPlanReason reason : ReprocessingPlanReason.values()) {
            DocumentUploadNode document = documentRepository.save(document("external-" + reason));
            SchemaReprocessingPlanNode plan = reason == ReprocessingPlanReason.SCHEMA_ACTIVATION
                ? plan(schema) : chunkPlan(schema);
            plan.setId("external-plan-" + reason);
            plan.setTotalDocuments(1);
            plan.setQueuedDocuments(0);
            plan.setRunningDocuments(1);
            if (reason == ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION) {
                plan.setTargetSnapshotJson("{\"documents\":{\"" + document.getId() + "\":{"
                    + "\"sourceSha256\":\"" + document.getSha256() + "\","
                    + "\"parserId\":\"text\",\"parserRevision\":\"text-v1\",\"fileFormat\":\"TXT\","
                    + "\"effectiveChunkerRevision\":\"effective\",\"effectiveProcessingOptions\":{}}}}");
            }
            planRepository.save(plan);
            assertThat(planRepository.claim(plan.getId(), "worker", start, start.plusSeconds(60))).isEqualTo(1);
            SchemaReprocessingItemNode item = itemRepository.save(item(plan, document, "external-item-" + reason));
            assertThat(itemRepository.claim(item.getId(), "worker", start, start.plusSeconds(60))).isEqualTo(1);
            DocumentProcessingRunNode run = new DocumentProcessingRunNode();
            run.setId("external-run-" + reason);
            run.setDocumentId(document.getId());
            run.setKnowledgeBaseId(knowledgeBaseId);
            run.setSourceSha256(document.getSha256());
            run.setParserId("text");
            run.setFileFormat("TXT");
            run.setRequestedOptionsJson("{}");
            run.setSavedDefaultsJson("{}");
            run.setEffectiveOptionsJson("{}");
            run.setEffectiveChunkerRevision("effective");
            run.setStatus(DocumentProcessingRunStatus.COMPLETED);
            run.setStage("COMPLETED");
            run.setStartedAt(start);
            run.setCompletedAt(start.plusSeconds(30));
            run.setActiveCompleted(true);
            processingRunRepository.save(run);

            recoveryService.recover();

            SchemaReprocessingItemNode recovered = itemRepository.findById(item.getId()).orElseThrow();
            assertThat(recovered.getStatus()).isEqualTo(SchemaReprocessingItemStatus.SUCCEEDED);
            assertThat(recovered.isRetryable()).isFalse();
            SchemaReprocessingPlanNode repaired = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(repaired.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.COMPLETED);
            assertThat(repaired.getSucceededDocuments()).isEqualTo(1);
            assertThat(repaired.getRunningDocuments()).isZero();
        }
    }

    @Test
    void malformedPlanDoesNotPreventUnrelatedPlanRecovery() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow-recovery-isolation\",\"version\":1,"
                + "\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}",
            knowledgeBaseId
        );
        DocumentUploadNode malformedDocument = documentRepository.save(document("document-malformed"));
        SchemaReprocessingPlanNode malformed = chunkPlan(schema);
        malformed.setId("malformed-plan");
        malformed.setTargetSnapshotJson("{invalid");
        malformed.setStatus(SchemaReprocessingPlanStatus.RUNNING);
        malformed.setQueuedDocuments(0);
        malformed.setRunningDocuments(1);
        malformed.setStartedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        malformed.setClaimedBy("expired-worker");
        malformed.setClaimedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        malformed.setClaimUntil(Instant.now().minus(1, ChronoUnit.HOURS));
        malformed.setTotalDocuments(1);
        planRepository.save(malformed);
        SchemaReprocessingItemNode malformedItem =
            item(malformed, malformedDocument, "malformed-item");
        malformedItem.setStatus(SchemaReprocessingItemStatus.RUNNING);
        malformedItem.setClaimedBy("expired-worker");
        malformedItem.setClaimedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        malformedItem.setClaimUntil(Instant.now().minus(1, ChronoUnit.HOURS));
        malformedItem.setStartedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        itemRepository.save(malformedItem);

        String otherKnowledgeBaseId = "workflow-recovery-other-" + UUID.randomUUID();
        knowledgeBaseLifecycleService.provision(otherKnowledgeBaseId, otherKnowledgeBaseId);
        SchemaDefinitionNode recoverableSchema =
            schemaRegistryService.createGeneratedInactiveSchema(
                "{\"name\":\"workflow-recovery-other\",\"version\":1,"
                    + "\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                    + "\"properties\":[{\"name\":\"id\",\"type\":\"string\","
                    + "\"required\":true}]}],\"relationships\":[]}",
                otherKnowledgeBaseId
            );
        SchemaDraftNode recoverableDraft = draft();
        recoverableDraft.setId("recoverable-draft");
        recoverableDraft.setKnowledgeBaseId(otherKnowledgeBaseId);
        recoverableDraft.setTargetName("workflow-recovery-other");
        draftRepository.save(recoverableDraft);
        DocumentUploadNode recoverableDocument = document("document-recoverable");
        recoverableDocument.setKnowledgeBaseId(otherKnowledgeBaseId);
        documentRepository.save(recoverableDocument);
        SchemaReprocessingPlanNode recoverable = plan(schema);
        recoverable.setId("recoverable-plan");
        recoverable.setDraftId(recoverableDraft.getId());
        recoverable.setKnowledgeBaseId(otherKnowledgeBaseId);
        recoverable.setSchemaId(recoverableSchema.getId());
        recoverable.setSchemaContentHash(recoverableSchema.getContentHash());
        planRepository.save(recoverable);
        itemRepository.save(item(recoverable, recoverableDocument, "recoverable-item"));

        recoveryService.recover();

        SchemaReprocessingPlanNode recovered =
            planRepository.findById(recoverable.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.INTERRUPTED);
        assertThat(recovered.getFailedDocuments()).isEqualTo(1);
        assertThat(planRepository.findById(malformed.getId()).orElseThrow().getStatus())
            .isEqualTo(SchemaReprocessingPlanStatus.RUNNING);
    }

    @Test
    void persistsChunkMigrationOutcomesAndRepairsPartialProgress() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow-migration\",\"version\":1,\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}",
            knowledgeBaseId
        );
        DocumentUploadNode succeededDocument = documentRepository.save(document("document-success"));
        DocumentUploadNode staleDocument = documentRepository.save(document("document-stale"));
        DocumentUploadNode blockedDocument = documentRepository.save(document("document-blocked"));
        SchemaReprocessingPlanNode plan = chunkPlan(schema);
        planRepository.save(plan);
        SchemaReprocessingItemNode succeeded = item(plan, succeededDocument, "item-success");
        SchemaReprocessingItemNode stale = item(plan, staleDocument, "item-stale");
        SchemaReprocessingItemNode blocked = item(plan, blockedDocument, "item-blocked");
        itemRepository.save(succeeded);
        itemRepository.save(stale);
        itemRepository.save(blocked);
        Instant claimedAt = Instant.now();
        complete(succeeded, SchemaReprocessingItemStatus.SUCCEEDED, null, false, claimedAt);
        complete(stale, SchemaReprocessingItemStatus.STALE_SOURCE, "SOURCE_CHANGED", true, claimedAt);
        complete(
            blocked,
            SchemaReprocessingItemStatus.BLOCKED_TARGET_CHANGED,
            "TARGET_CHANGED",
            true,
            claimedAt
        );

        SchemaReprocessingPlanNode repaired = recoveryService.repairCounters(plan.getId());

        assertThat(repaired.getReason()).isEqualTo(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        assertThat(repaired.getSelection()).isEqualTo(ChunkReprocessingSelection.ALL);
        assertThat(repaired.getStatus()).isEqualTo(SchemaReprocessingPlanStatus.PARTIAL);
        assertThat(repaired.getSucceededDocuments()).isEqualTo(1);
        assertThat(repaired.getStaleDocuments()).isEqualTo(1);
        assertThat(repaired.getBlockedDocuments()).isEqualTo(1);
    }

    @Test
    void filtersReprocessingHistoryBeforePagingAndPreservesNullSelectionSemantics() {
        SchemaDefinitionNode schema = schemaRegistryService.createGeneratedInactiveSchema(
            "{\"name\":\"workflow-filters\",\"version\":1,\"nodes\":[{\"label\":\"Thing\",\"key\":\"id\","
                + "\"properties\":[{\"name\":\"id\",\"type\":\"string\",\"required\":true}]}],"
                + "\"relationships\":[]}",
            knowledgeBaseId
        );
        SchemaReprocessingPlanNode schemaPlan = plan(schema);
        schemaPlan.setId("history-schema");
        schemaPlan.setStatus(SchemaReprocessingPlanStatus.COMPLETED);
        schemaPlan.setTotalDocuments(0);
        schemaPlan.setQueuedDocuments(0);
        schemaPlan.setCompletedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        schemaPlan.setCreatedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        planRepository.save(schemaPlan);
        SchemaReprocessingPlanNode chunkPlan = chunkPlan(schema);
        chunkPlan.setId("history-chunk");
        chunkPlan.setStatus(SchemaReprocessingPlanStatus.COMPLETED);
        chunkPlan.setTotalDocuments(0);
        chunkPlan.setQueuedDocuments(0);
        chunkPlan.setCompletedAt(Instant.now());
        chunkPlan.setCreatedAt(Instant.now());
        planRepository.save(chunkPlan);

        assertThat(planRepository.findPageByFilters(
            knowledgeBaseId, null, ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION,
            ChunkReprocessingSelection.ALL, SchemaReprocessingPlanStatus.COMPLETED, PageRequest.of(0, 1)
        ).getTotalElements()).isEqualTo(1);
        assertThat(planRepository.findPageByFilters(
            knowledgeBaseId, null, ReprocessingPlanReason.SCHEMA_ACTIVATION,
            null, SchemaReprocessingPlanStatus.COMPLETED, PageRequest.of(0, 10)
        ).getContent()).singleElement().extracting(SchemaReprocessingPlanNode::getSelection)
            .isNull();
        assertThat(planRepository.findPageByFilters(
            knowledgeBaseId, null, null, null, null, PageRequest.of(0, 10)
        ).getContent()).extracting(SchemaReprocessingPlanNode::getId)
            .containsExactly("history-chunk", "history-schema");
    }

    private SchemaDraftNode draft() {
        SchemaDraftNode value = new SchemaDraftNode();
        value.setId("workflow-draft");
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setTargetName("workflow");
        value.setTargetVersion(1);
        value.setStatus(SchemaDraftStatus.OPEN);
        value.setGuidanceJson("{}");
        value.setGuidanceFingerprint("a".repeat(64));
        value.setActiveAiProfileId(profile.getId());
        value.setActiveAiProfileRevision(profile.getRevision());
        value.setCreatedAt(Instant.now());
        value.setUpdatedAt(Instant.now());
        return value;
    }

    private SchemaDraftAnalysisRunNode analysisRun() {
        SchemaDraftAnalysisRunNode value = new SchemaDraftAnalysisRunNode();
        value.setId("analysis-run");
        value.setDraftId(draft.getId());
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setStatus(SchemaDraftAnalysisStatus.COMPLETED);
        value.setGuidanceFingerprint(draft.getGuidanceFingerprint());
        value.setSourceSnapshotJson("[]");
        value.setSourceMembershipFingerprint("b".repeat(64));
        value.setAiProfileId(profile.getId());
        value.setAiProfileRevision(profile.getRevision());
        value.setConfiguredTimeoutSeconds(30);
        value.setPromptRevision("prompt-v1");
        value.setCandidateRevision("candidate-v1");
        value.setSnapshotFingerprint("c".repeat(64));
        value.setCreatedAt(Instant.now());
        value.setStartedAt(Instant.now());
        value.setCompletedAt(Instant.now());
        return value;
    }

    private SchemaDraftAggregateRevisionNode aggregate(SchemaDraftAnalysisRunNode analysis) {
        SchemaDraftAggregateRevisionNode value = new SchemaDraftAggregateRevisionNode();
        value.setId("aggregate-1");
        value.setDraftId(draft.getId());
        value.setRunId(analysis.getId());
        value.setRevision(1);
        value.setCandidatesJson("[]");
        value.setConflictsJson("[]");
        value.setWarningsJson("[]");
        value.setSchemaJson("{}");
        value.setContentHash("d".repeat(64));
        value.setDiffBaselineType(DiffBaselineType.EMPTY);
        value.setCreatedAt(Instant.now());
        return value;
    }

    private SchemaDraftEvaluationRunNode evaluationRun(String id) {
        SchemaDraftEvaluationRunNode value = new SchemaDraftEvaluationRunNode();
        value.setId(id);
        value.setDraftId(draft.getId());
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setStatus(SchemaDraftEvaluationStatus.QUEUED);
        value.setAggregateRevisionId(aggregate.getId());
        value.setProjectionJson("{}");
        value.setProjectionContentHash("e".repeat(64));
        value.setGuidanceJson("{}");
        value.setDecisionsJson("[]");
        value.setDecisionsFingerprint("f".repeat(64));
        value.setDocumentSnapshotJson("[]");
        value.setAiProfileId(profile.getId());
        value.setAiProfileRevision(profile.getRevision());
        value.setPromptRevision("prompt-v1");
        value.setContractRevision("contract-v1");
        value.setSettingsJson("{}");
        value.setSnapshotFingerprint("1".repeat(64));
        value.setRetryable(true);
        value.setCreatedAt(Instant.now());
        return value;
    }

    private SchemaDraftPublicationNode publication(String id, SchemaDefinitionNode schema) {
        SchemaDraftPublicationNode value = new SchemaDraftPublicationNode();
        value.setId(id);
        value.setDraftId(draft.getId());
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setTargetIdentity("workflow:1");
        value.setAggregateRevisionId(aggregate.getId());
        value.setProjectionContentHash(schema.getContentHash());
        value.setStatus(SchemaDraftPublicationStatus.PENDING);
        value.setRetryable(true);
        value.setCreatedAt(Instant.now());
        return value;
    }

    private SchemaReprocessingPlanNode plan(SchemaDefinitionNode schema) {
        SchemaReprocessingPlanNode value = new SchemaReprocessingPlanNode();
        value.setId("plan-1");
        value.setDraftId(draft.getId());
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setSchemaId(schema.getId());
        value.setSchemaContentHash(schema.getContentHash());
        value.setAiProfileId(profile.getId());
        value.setAiProfileRevision(profile.getRevision());
        value.setProcessingOptionsJson("{}");
        value.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        value.setTotalDocuments(1);
        value.setQueuedDocuments(1);
        value.setCreatedAt(Instant.now());
        return value;
    }

    private DocumentUploadNode document() {
        return document("document-1");
    }

    private DocumentUploadNode document(String id) {
        DocumentUploadNode value = new DocumentUploadNode();
        value.setId(id);
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setOriginalFilename("document.txt");
        value.setContentType("text/plain");
        value.setSizeBytes(4);
        value.setSha256(io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkHashes.sha256(id));
        value.setContentUri("file:///tmp/" + id);
        value.setStatus(DocumentStatus.COMPLETED);
        value.setUploadedAt(Instant.now());
        value.setProcessedAt(Instant.now());
        return value;
    }

    private SchemaReprocessingPlanNode chunkPlan(SchemaDefinitionNode schema) {
        SchemaReprocessingPlanNode value = new SchemaReprocessingPlanNode();
        value.setId("chunk-plan-1");
        value.setReason(ReprocessingPlanReason.CHUNK_STRATEGY_MIGRATION);
        value.setSelection(ChunkReprocessingSelection.ALL);
        value.setExpectedChunkerRevision("chunker_" + "3".repeat(64));
        value.setTargetSnapshotJson("{\"documents\":{}}");
        value.setEmbeddingSpaceId("es_" + "4".repeat(64));
        value.setKnowledgeBaseId(knowledgeBaseId);
        value.setSchemaId(schema.getId());
        value.setSchemaContentHash(schema.getContentHash());
        value.setAiProfileId(profile.getId());
        value.setAiProfileRevision(profile.getRevision());
        value.setProcessingOptionsJson("{}");
        value.setStatus(SchemaReprocessingPlanStatus.QUEUED);
        value.setTotalDocuments(3);
        value.setQueuedDocuments(3);
        value.setCreatedAt(Instant.now());
        return value;
    }

    private void complete(
        SchemaReprocessingItemNode item,
        SchemaReprocessingItemStatus status,
        String failureCategory,
        boolean retryable,
        Instant claimedAt
    ) {
        assertThat(itemRepository.claim(
            item.getId(),
            "worker-" + item.getId(),
            claimedAt,
            claimedAt.plus(5, ChronoUnit.MINUTES)
        )).isEqualTo(1);
        assertThat(itemRepository.complete(
            item.getId(),
            "worker-" + item.getId(),
            status,
            failureCategory,
            retryable,
            Instant.now()
        )).isEqualTo(1);
    }

    private SchemaReprocessingItemNode item(
        SchemaReprocessingPlanNode plan, DocumentUploadNode document, String id
    ) {
        SchemaReprocessingItemNode value = new SchemaReprocessingItemNode();
        value.setId(id);
        value.setPlanId(plan.getId());
        value.setDocumentId(document.getId());
        value.setDocumentSha256(document.getSha256());
        value.setStatus(SchemaReprocessingItemStatus.QUEUED);
        value.setRetryable(true);
        return value;
    }
}
