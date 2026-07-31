package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DiffBaselineType;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
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
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import io.github.vfedoriv.graphrag.service.SchemaReprocessingRecoveryService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@IntegrationTest
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
    @Autowired private SchemaReprocessingRecoveryService recoveryService;

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
        SchemaReprocessingPlanNode competing = plan(schema);
        competing.setId("plan-competing");

        assertThatThrownBy(() -> planRepository.save(competing))
            .isInstanceOf(DataIntegrityViolationException.class);
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
        value.setSha256(io.github.vfedoriv.graphrag.document.chunking.ChunkHashes.sha256(id));
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
