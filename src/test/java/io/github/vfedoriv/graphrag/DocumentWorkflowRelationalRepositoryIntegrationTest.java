package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationType;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentStorageMutationRepository;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentStorageMutationService;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RelationalIntegrationTest
@Import(DocumentWorkflowRelationalRepositoryIntegrationTest.RollbackConfiguration.class)
class DocumentWorkflowRelationalRepositoryIntegrationTest {
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    @Autowired
    private DocumentUploadRepository documentRepository;
    @Autowired
    private DocumentProcessingRunRepository processingRunRepository;
    @Autowired
    private DocumentStorageMutationRepository mutationRepository;
    @Autowired
    private DocumentStorageMutationService mutationService;
    @Autowired
    private RollbackProbe rollbackProbe;

    private String firstKnowledgeBaseId;
    private String secondKnowledgeBaseId;

    @BeforeEach
    void prepare() {
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        String suffix = UUID.randomUUID().toString();
        firstKnowledgeBaseId = "workflow-a-" + suffix;
        secondKnowledgeBaseId = "workflow-b-" + suffix;
        knowledgeBaseLifecycleService.provision(firstKnowledgeBaseId, firstKnowledgeBaseId);
        knowledgeBaseLifecycleService.provision(secondKnowledgeBaseId, secondKnowledgeBaseId);
    }

    @Test
    void scopesDeduplicationAndPaginatesDeterministically() {
        String digest = "a".repeat(64);
        DocumentUploadNode first = document("doc-a", firstKnowledgeBaseId, digest, Instant.parse("2026-01-01T00:00:00Z"));
        DocumentUploadNode second = document("doc-b", secondKnowledgeBaseId, digest, Instant.parse("2026-01-02T00:00:00Z"));
        documentRepository.save(first);
        documentRepository.save(second);

        assertThatThrownBy(() -> documentRepository.save(
            document("doc-duplicate", firstKnowledgeBaseId, digest, Instant.parse("2026-01-03T00:00:00Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);

        documentRepository.save(document(
            "doc-newest",
            firstKnowledgeBaseId,
            "b".repeat(64),
            Instant.parse("2026-01-04T00:00:00Z")
        ));
        Page<DocumentUploadNode> page = documentRepository.findPageByKnowledgeBaseId(
            firstKnowledgeBaseId,
            PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("uploadedAt"),
                org.springframework.data.domain.Sort.Order.desc("id")
            ))
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(DocumentUploadNode::getId).containsExactly("doc-newest");
        assertThat(documentRepository.findByKnowledgeBaseIdAndSha256(secondKnowledgeBaseId, digest))
            .isPresent();
    }

    @Test
    void enforcesOptimisticVersionsAndTransactionalRollback() {
        DocumentUploadNode saved = documentRepository.save(document(
            "doc-versioned",
            firstKnowledgeBaseId,
            "c".repeat(64),
            Instant.now()
        ));
        DocumentUploadNode firstCopy = documentRepository.findById(saved.getId()).orElseThrow();
        DocumentUploadNode staleCopy = documentRepository.findById(saved.getId()).orElseThrow();
        firstCopy.setOriginalFilename("winner.txt");
        documentRepository.save(firstCopy);
        staleCopy.setOriginalFilename("stale.txt");

        assertThatThrownBy(() -> documentRepository.save(staleCopy))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThatThrownBy(() -> rollbackProbe.saveAndFail(document(
            "doc-rolled-back",
            firstKnowledgeBaseId,
            "d".repeat(64),
            Instant.now()
        ))).isInstanceOf(IllegalStateException.class);
        assertThat(documentRepository.findById("doc-rolled-back")).isEmpty();
    }

    @Test
    void enforcesRunLifecycleAndSingleActiveCompletion() {
        documentRepository.save(document("doc-runs", firstKnowledgeBaseId, "e".repeat(64), Instant.now()));
        DocumentProcessingRunNode first = completedRun("run-first", "doc-runs", true);
        processingRunRepository.save(first);

        assertThatThrownBy(() -> processingRunRepository.save(completedRun("run-second", "doc-runs", true)))
            .isInstanceOf(DataIntegrityViolationException.class);

        processingRunRepository.deactivateOtherCompletedRuns("doc-runs", "run-second");
        DocumentProcessingRunNode second = processingRunRepository.save(completedRun("run-second", "doc-runs", true));

        assertThat(second.isActiveCompleted()).isTrue();
        assertThat(second.getChunkStrategy()).isEqualTo("fixed-character");
        assertThat(second.getChunkSettingsHash()).isEqualTo("a".repeat(64));
        assertThat(second.getTokenizerId()).isEqualTo("cl100k_base");
        assertThat(second.getTokenCountMode()).isEqualTo("EXACT");
        assertThat(second.getEffectiveChunkerRevision()).startsWith("chunker_");
        assertThat(processingRunRepository.findByDocumentIdOrderByStartedAtAsc("doc-runs"))
            .hasSize(2)
            .extracting(DocumentProcessingRunNode::isActiveCompleted)
            .containsExactly(false, true);
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO app.document_processing_run
                (id, document_id, knowledge_base_id, source_sha256, parser_id, file_format,
                 requested_options_json, saved_defaults_json, effective_options_json,
                 status, stage, started_at, active_completed, retry_count, version)
            VALUES (?, ?, ?, ?, 'parser', 'txt', '{}', '{}', '{}',
                    'COMPLETED', 'COMPLETED', CURRENT_TIMESTAMP, false, 0, 0)
            """,
            "invalid-terminal",
            "doc-runs",
            firstKnowledgeBaseId,
            "e".repeat(64)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void claimsMutationWorkOnceAndMakesFailureRetryable() {
        DocumentUploadNode target = documentRepository.save(document(
            "journal-target",
            firstKnowledgeBaseId,
            "f".repeat(64),
            Instant.now()
        ));
        DocumentStorageMutationNode mutation = mutationService.begin(
            DocumentStorageMutationType.DELETE,
            firstKnowledgeBaseId,
            target.getId(),
            "file:///tmp/missing",
            null
        );
        documentRepository.delete(target);
        Instant now = Instant.now();

        List<DocumentStorageMutationNode> firstClaim = mutationRepository.claimPending(
            "worker-a",
            now,
            now.plusSeconds(60),
            10
        );
        List<DocumentStorageMutationNode> competingClaim = mutationRepository.claimPending(
            "worker-b",
            now,
            now.plusSeconds(60),
            10
        );
        mutationService.recordFailure(mutation.getId(), new IllegalStateException("retry"));
        List<DocumentStorageMutationNode> retryClaim = mutationRepository.claimPending(
            "worker-b",
            now.plusSeconds(1),
            now.plusSeconds(61),
            10
        );

        assertThat(firstClaim).extracting(DocumentStorageMutationNode::getId).containsExactly(mutation.getId());
        assertThat(competingClaim).isEmpty();
        assertThat(retryClaim).extracting(DocumentStorageMutationNode::getId).containsExactly(mutation.getId());
        assertThat(mutationRepository.findById(mutation.getId())).isPresent();
    }

    private DocumentUploadNode document(String id, String knowledgeBaseId, String sha256, Instant uploadedAt) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalFilename(id + ".txt");
        document.setContentType("text/plain");
        document.setSizeBytes(1);
        document.setSha256(sha256);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setUploadedAt(uploadedAt);
        return document;
    }

    private DocumentProcessingRunNode completedRun(String id, String documentId, boolean active) {
        DocumentProcessingRunNode run = new DocumentProcessingRunNode();
        run.setId(id);
        run.setDocumentId(documentId);
        run.setKnowledgeBaseId(firstKnowledgeBaseId);
        run.setSourceSha256("e".repeat(64));
        run.setParserId("parser");
        run.setFileFormat("txt");
        run.setRequestedOptionsJson("{}");
        run.setSavedDefaultsJson("{}");
        run.setEffectiveOptionsJson("{}");
        run.setChunkStrategy("fixed-character");
        run.setChunkStrategyRevision("fixed-character-v1");
        run.setChunkSettingsHash("a".repeat(64));
        run.setTokenizerId("cl100k_base");
        run.setTokenizerRevision("cl100k-base-jtokkit-1.1.0");
        run.setTokenCountMode("EXACT");
        run.setEffectiveChunkerRevision("chunker_" + "b".repeat(64));
        run.setStatus(DocumentProcessingRunStatus.COMPLETED);
        run.setStage("COMPLETED");
        run.setStartedAt("run-first".equals(id)
            ? Instant.parse("2026-01-01T00:00:00Z")
            : Instant.parse("2026-01-02T00:00:00Z"));
        run.setCompletedAt(Instant.parse("2026-01-03T00:00:00Z"));
        run.setActiveCompleted(active);
        return run;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackConfiguration {
        @Bean
        RollbackProbe rollbackProbe(DocumentUploadRepository repository) {
            return new RollbackProbe(repository);
        }
    }

    static class RollbackProbe {
        private final DocumentUploadRepository repository;

        RollbackProbe(DocumentUploadRepository repository) {
            this.repository = repository;
        }

        @RelationalTransactional
        public void saveAndFail(DocumentUploadNode document) {
            repository.save(document);
            throw new IllegalStateException("rollback");
        }
    }
}
