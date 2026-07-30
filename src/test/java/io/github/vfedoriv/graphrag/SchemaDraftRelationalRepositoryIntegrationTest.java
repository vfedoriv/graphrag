package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DiffBaselineType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftReviewState;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceResultRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftStorageMutationRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@IntegrationTest
class SchemaDraftRelationalRepositoryIntegrationTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    @Autowired private KnowledgeBaseService knowledgeBaseService;
    @Autowired private SchemaDraftRepository draftRepository;
    @Autowired private SchemaDraftSourceRepository sourceRepository;
    @Autowired private SchemaDraftSourceRevisionRepository revisionRepository;
    @Autowired private SchemaDraftAnalysisRunRepository runRepository;
    @Autowired private SchemaDraftSourceResultRepository resultRepository;
    @Autowired private SchemaDraftAggregateRevisionRepository aggregateRepository;
    @Autowired private SchemaDraftDecisionRepository decisionRepository;
    @Autowired private SchemaDraftStorageMutationRepository mutationRepository;

    private String firstKnowledgeBaseId;
    private String secondKnowledgeBaseId;

    @BeforeEach
    void prepare() {
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        String suffix = UUID.randomUUID().toString();
        firstKnowledgeBaseId = "draft-rel-a-" + suffix;
        secondKnowledgeBaseId = "draft-rel-b-" + suffix;
        knowledgeBaseLifecycleService.provision(firstKnowledgeBaseId, firstKnowledgeBaseId);
        knowledgeBaseLifecycleService.provision(secondKnowledgeBaseId, secondKnowledgeBaseId);
    }

    @Test
    void preservesTextPayloadsAndRelationalHistoryExactly() {
        String guidance = "{\"guidance\":{\"domainDescription\":\"Ångström 東京\"}}";
        SchemaDraftNode draft = draftRepository.save(draft("draft-payload", firstKnowledgeBaseId, guidance));
        SchemaDraftSourceNode source = sourceRepository.save(source(draft, "source-payload"));
        revisionRepository.save(revision(source));
        SchemaDraftAnalysisRunNode run = runRepository.save(completedRun(draft, "run-payload"));
        String candidates = "[{\"identity\":\"node:Person\",\"description\":\"line 1\\nline 2\"}]";
        String aliases = "[{\"from\":\"Human\",\"to\":\"Person\"}]";
        resultRepository.save(result(draft, source, run, candidates, aliases));
        SchemaDraftAggregateRevisionNode aggregate = aggregateRepository.save(
            aggregate(draft, run, candidates));

        assertThat(draftRepository.findById(draft.getId()).orElseThrow().getGuidanceJson())
            .isEqualTo(guidance);
        SchemaDraftSourceResultNode storedResult = resultRepository
            .findByRunIdOrderByCreatedAtAsc(run.getId()).getFirst();
        assertThat(storedResult.getCandidatesJson()).isEqualTo(candidates);
        assertThat(storedResult.getAliasesJson()).isEqualTo(aliases);
        assertThat(aggregateRepository.findById(aggregate.getId()).orElseThrow().getSchemaJson())
            .isEqualTo("{\"name\":\"payload\",\"version\":1}");
    }

    @Test
    void enforcesOwnershipRevisionSequenceReuseAndOptimisticConstraints() {
        SchemaDraftNode draft = draftRepository.save(draft("draft-constraints", firstKnowledgeBaseId, "{}"));
        SchemaDraftNode firstCopy = draftRepository.findById(draft.getId()).orElseThrow();
        SchemaDraftNode staleCopy = draftRepository.findById(draft.getId()).orElseThrow();
        firstCopy.setTargetName("winner");
        draftRepository.save(firstCopy);
        staleCopy.setTargetName("stale");
        assertThatThrownBy(() -> draftRepository.save(staleCopy))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        SchemaDraftSourceNode crossOwned = source(draft, "source-cross-owner");
        crossOwned.setKnowledgeBaseId(secondKnowledgeBaseId);
        assertThatThrownBy(() -> sourceRepository.save(crossOwned))
            .isInstanceOf(DataIntegrityViolationException.class);

        SchemaDraftSourceNode source = sourceRepository.save(source(draft, "source-constraints"));
        revisionRepository.save(revision(source));
        SchemaDraftSourceRevisionNode duplicateRevision = revision(source);
        duplicateRevision.setId("duplicate-revision");
        assertThatThrownBy(() -> revisionRepository.save(duplicateRevision))
            .isInstanceOf(DataIntegrityViolationException.class);

        SchemaDraftDecisionNode first = decision(draft, "decision-1", 1);
        decisionRepository.save(first);
        assertThatThrownBy(() -> decisionRepository.save(decision(draft, "decision-2", 1)))
            .isInstanceOf(DataIntegrityViolationException.class);

        SchemaDraftAnalysisRunNode run = runRepository.save(completedRun(draft, "run-constraints"));
        resultRepository.save(result(draft, source, run, "[]", "[]"));
        SchemaDraftSourceResultNode duplicateReuse = result(draft, source, run, "[]", "[]");
        duplicateReuse.setId("duplicate-reuse");
        assertThatThrownBy(() -> resultRepository.save(duplicateReuse))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void claimsOnlyOneRunAndRetainsMutationJournalAfterDraftDeletion() {
        SchemaDraftNode draft = draftRepository.save(draft("draft-claim", firstKnowledgeBaseId, "{}"));
        SchemaDraftAnalysisRunNode first = runRepository.save(runningRun(draft, "run-first"));
        runRepository.save(runningRun(draft, "run-second"));

        assertThat(draftRepository.reserveAnalysis(draft.getId(), first.getId(), draft.getRevision())).isEqualTo(1);
        assertThat(draftRepository.reserveAnalysis(draft.getId(), "run-second", draft.getRevision())).isZero();
        assertThat(draftRepository.releaseAnalysis(draft.getId(), "run-second")).isZero();
        assertThat(draftRepository.findById(draft.getId()).orElseThrow().getRunningAnalysisRunId())
            .isEqualTo(first.getId());

        SchemaDraftStorageMutationNode mutation = new SchemaDraftStorageMutationNode();
        mutation.setId("mutation-retained");
        mutation.setType(SchemaDraftStorageMutationType.DELETE);
        mutation.setState(SchemaDraftStorageMutationState.PENDING);
        mutation.setDraftId(draft.getId());
        mutation.setSourceId("source-gone");
        mutation.setContentUri("file:///tmp/source-gone");
        mutation.setCreatedAt(Instant.now());
        mutation.setUpdatedAt(Instant.now());
        mutationRepository.save(mutation);

        assertThat(draftRepository.deleteOwnedGraph(firstKnowledgeBaseId, draft.getId())).isEqualTo(1);
        SchemaDraftStorageMutationNode retained = mutationRepository.findById(mutation.getId()).orElseThrow();
        assertThat(retained.getDraftId()).isNull();
        assertThat(retained.getSourceId()).isEqualTo("source-gone");
    }

    private SchemaDraftNode draft(String id, String knowledgeBaseId, String guidanceJson) {
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(knowledgeBaseId);
        SchemaDraftNode draft = new SchemaDraftNode();
        draft.setId(id);
        draft.setKnowledgeBaseId(knowledgeBaseId);
        draft.setTargetName(id);
        draft.setTargetVersion(1);
        draft.setStatus(SchemaDraftStatus.OPEN);
        draft.setGuidanceJson(guidanceJson);
        draft.setGuidanceFingerprint("a".repeat(64));
        draft.setActiveAiProfileId(profile.getId());
        draft.setActiveAiProfileRevision(profile.getRevision());
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        return draft;
    }

    private SchemaDraftSourceNode source(SchemaDraftNode draft, String id) {
        SchemaDraftSourceNode source = new SchemaDraftSourceNode();
        source.setId(id);
        source.setDraftId(draft.getId());
        source.setKnowledgeBaseId(draft.getKnowledgeBaseId());
        source.setType(SchemaDraftSourceType.TEXT);
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        source.setName(id);
        source.setContentType("text/plain");
        source.setSizeBytes(7);
        source.setSha256("b".repeat(64));
        source.setContentUri("file:///tmp/" + id);
        source.setCreatedAt(Instant.now());
        source.setUpdatedAt(Instant.now());
        return source;
    }

    private SchemaDraftSourceRevisionNode revision(SchemaDraftSourceNode source) {
        SchemaDraftSourceRevisionNode revision = new SchemaDraftSourceRevisionNode();
        revision.setId(source.getId() + ":0");
        revision.setDraftId(source.getDraftId());
        revision.setSourceId(source.getId());
        revision.setStatus(source.getStatus());
        revision.setSha256(source.getSha256());
        revision.setContentUri(source.getContentUri());
        revision.setCreatedAt(Instant.now());
        return revision;
    }

    private SchemaDraftAnalysisRunNode runningRun(SchemaDraftNode draft, String id) {
        AiProfileNode profile = knowledgeBaseService.activeAiProfile(draft.getKnowledgeBaseId());
        SchemaDraftAnalysisRunNode run = new SchemaDraftAnalysisRunNode();
        run.setId(id);
        run.setDraftId(draft.getId());
        run.setKnowledgeBaseId(draft.getKnowledgeBaseId());
        run.setStatus(SchemaDraftAnalysisStatus.RUNNING);
        run.setGuidanceFingerprint(draft.getGuidanceFingerprint());
        run.setSourceSnapshotJson("[]");
        run.setSourceMembershipFingerprint("c".repeat(64));
        run.setAiProfileId(profile.getId());
        run.setAiProfileRevision(profile.getRevision());
        run.setConfiguredTimeoutSeconds(30);
        run.setPromptRevision("prompt-v1");
        run.setCandidateRevision("candidate-v1");
        run.setSnapshotFingerprint("d".repeat(64));
        run.setCreatedAt(Instant.now());
        run.setStartedAt(Instant.now());
        return run;
    }

    private SchemaDraftAnalysisRunNode completedRun(SchemaDraftNode draft, String id) {
        SchemaDraftAnalysisRunNode run = runningRun(draft, id);
        run.setStatus(SchemaDraftAnalysisStatus.COMPLETED);
        run.setTotalSources(1);
        run.setSucceededSources(1);
        run.setCompletedAt(Instant.now());
        return run;
    }

    private SchemaDraftSourceResultNode result(
        SchemaDraftNode draft,
        SchemaDraftSourceNode source,
        SchemaDraftAnalysisRunNode run,
        String candidates,
        String aliases
    ) {
        SchemaDraftSourceResultNode result = new SchemaDraftSourceResultNode();
        result.setId(UUID.randomUUID().toString());
        result.setDraftId(draft.getId());
        result.setRunId(run.getId());
        result.setSourceId(source.getId());
        result.setSourceSha256(source.getSha256());
        result.setReuseKey("e".repeat(64));
        result.setStatus(SchemaDraftSourceResultStatus.SUCCEEDED);
        result.setCandidatesJson(candidates);
        result.setAliasesJson(aliases);
        result.setChunkCount(1);
        result.setCreatedAt(Instant.now());
        result.setCompletedAt(Instant.now());
        return result;
    }

    private SchemaDraftAggregateRevisionNode aggregate(
        SchemaDraftNode draft,
        SchemaDraftAnalysisRunNode run,
        String candidates
    ) {
        SchemaDraftAggregateRevisionNode aggregate = new SchemaDraftAggregateRevisionNode();
        aggregate.setId("aggregate-payload");
        aggregate.setDraftId(draft.getId());
        aggregate.setRunId(run.getId());
        aggregate.setRevision(1);
        aggregate.setCandidatesJson(candidates);
        aggregate.setConflictsJson("[]");
        aggregate.setWarningsJson("[]");
        aggregate.setSchemaJson("{\"name\":\"payload\",\"version\":1}");
        aggregate.setContentHash("f".repeat(64));
        aggregate.setDiffBaselineType(DiffBaselineType.EMPTY);
        aggregate.setCreatedAt(Instant.now());
        return aggregate;
    }

    private SchemaDraftDecisionNode decision(SchemaDraftNode draft, String id, long sequence) {
        SchemaDraftDecisionNode decision = new SchemaDraftDecisionNode();
        decision.setId(id);
        decision.setDraftId(draft.getId());
        decision.setSequence(sequence);
        decision.setType(SchemaDraftDecisionType.ACCEPT);
        decision.setReviewState(SchemaDraftReviewState.ACCEPTED);
        decision.setCandidateIdentity("node:Person");
        decision.setCreatedAt(Instant.now());
        return decision;
    }
}
