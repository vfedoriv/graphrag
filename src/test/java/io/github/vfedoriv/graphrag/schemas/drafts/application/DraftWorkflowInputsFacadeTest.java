package io.github.vfedoriv.graphrag.schemas.drafts.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.*;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.*;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.*;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.*;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DraftWorkflowInputsFacadeTest {
    private final SchemaDraftLifecycleService lifecycle = mock(SchemaDraftLifecycleService.class);
    private final SchemaDraftReviewService review = mock(SchemaDraftReviewService.class);
    private final SchemaDraftAggregateRevisionRepository aggregates = mock(SchemaDraftAggregateRevisionRepository.class);
    private final SchemaDraftConflictRepository conflicts = mock(SchemaDraftConflictRepository.class);
    private final SchemaDraftDecisionRepository decisions = mock(SchemaDraftDecisionRepository.class);
    private final SchemaDraftSourceResultRepository results = mock(SchemaDraftSourceResultRepository.class);
    private final SchemaDraftJsonSupport json = new SchemaDraftJsonSupport(new io.github.vfedoriv.graphrag.config.LegacyJacksonConfiguration().legacyObjectMapper());
    private final DraftWorkflowInputsFacade facade = new DraftWorkflowInputsFacade(lifecycle, review, aggregates, conflicts, decisions, results, json, new SchemaDraftGuidanceMapper(json, new ObjectMapper()));

    @Test void admissionReturnsDetachedFactsAndPreservesScopeAndRevision() {
        SchemaDraftNode draft = draft();
        when(lifecycle.requireMutable("kb", "draft", 7)).thenReturn(draft);
        DraftAdmissions.Draft facts = facade.requireMutable("kb", "draft", 7);
        draft.setRevision(8);
        draft.setTargetName("changed");
        assertThat(facts.revision()).isEqualTo(7);
        assertThat(facts.targetName()).isEqualTo("target");
        when(lifecycle.requireOwned("foreign", "draft")).thenThrow(new NotFoundException("foreign"));
        assertThatThrownBy(() -> facade.requireOwned("foreign", "draft")).isInstanceOf(NotFoundException.class);
    }

    @Test void contributorsCopyHashesAndHistoricalFallbackIds() {
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft());
        List<String> hashes = new ArrayList<>(List.of("document", "file", "text"));
        List<String> ids = new ArrayList<>(List.of("old-doc"));
        when(results.findContributingSourceSha256s("draft")).thenReturn(hashes);
        when(results.findHistoricalContributingDocumentIds("draft")).thenReturn(ids);
        DraftContributors.Contributors facts = facade.contributors("kb", "draft");
        hashes.clear(); ids.clear();
        assertThat(facts.sha256s()).containsExactlyInAnyOrder("document", "file", "text");
        assertThat(facts.historicalDocumentIds()).containsExactly("old-doc");
        assertThatThrownBy(() -> facts.sha256s().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void reviewCapturesCanonicalDecisionTimestampAndDetachesProjection() {
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft());
        Map<String, Object> schema = new LinkedHashMap<>(Map.of("name", "target"));
        when(review.projection("kb", "draft")).thenReturn(new ProjectionResponse("aggregate", 7, schema, true));
        Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");
        when(review.decisions("kb", "draft")).thenReturn(List.of(new DecisionResponse("decision", 1, 7,
            SchemaDraftDecisionType.ACCEPT, SchemaDraftReviewState.ACCEPTED, "node:A", null, Map.of("label", "A"), "reason", timestamp)));
        DraftReviewInputs.Projection facts = facade.projection("kb", "draft");
        schema.put("name", "changed");
        assertThat(facts.schemaJson()).isEqualTo("{\"name\":\"target\"}");
        assertThat(facade.canonicalDecisions("kb", "draft")).contains("\"createdAt\":\"2026-01-02T03:04:05Z\"");
    }

    @Test void rejectsForeignAggregateInsteadOfReturningReadinessInputs() {
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft());
        SchemaDraftAggregateRevisionNode foreign = new SchemaDraftAggregateRevisionNode();
        foreign.setDraftId("another");
        when(aggregates.findById("aggregate")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> facade.aggregate("kb", "draft", "aggregate")).isInstanceOf(NotFoundException.class);
    }

    @Test void aggregateReturnsDetachedReadinessAndSupportInputs() {
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft());
        SchemaDraftAggregateRevisionNode aggregate = new SchemaDraftAggregateRevisionNode();
        aggregate.setDraftId("draft"); aggregate.setCandidatesJson("[{\"supportCount\":1}]");
        when(aggregates.findById("aggregate")).thenReturn(Optional.of(aggregate));
        SchemaDraftDecisionNode decision = new SchemaDraftDecisionNode(); decision.setCandidateIdentity("node:A");
        when(decisions.findByDraftIdOrderBySequenceAsc("draft")).thenReturn(List.of(decision));
        SchemaDraftConflictNode conflict = new SchemaDraftConflictNode(); conflict.setId("c");
        conflict.setType(SchemaDraftConflictType.TYPE); conflict.setCoordinate("node:A");
        when(conflicts.findByAggregateRevision("draft", "aggregate")).thenReturn(List.of(conflict));
        DraftReviewInputs.Aggregate facts = facade.aggregate("kb", "draft", "aggregate");
        aggregate.setCandidatesJson("[]"); conflict.setResolved(true); decision.setCandidateIdentity("node:B");
        assertThat(facts.candidatesJson()).isEqualTo("[{\"supportCount\":1}]");
        assertThat(facts.decidedIdentities()).containsExactly("node:A");
        assertThat(facts.conflicts()).containsExactly(new DraftReviewInputs.Conflict("c", "TYPE", "node:A", false));
        assertThatThrownBy(() -> facts.conflicts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(facade.intendedQuestions("{\"guidance\":{\"intendedQuestions\":[\"Who?\"]}}"))
            .containsExactly("Who?");
    }

    private SchemaDraftNode draft() {
        SchemaDraftNode draft = new SchemaDraftNode();
        draft.setId("draft"); draft.setKnowledgeBaseId("kb"); draft.setRevision(7);
        draft.setTargetName("target"); draft.setTargetVersion(2); draft.setStatus(SchemaDraftStatus.OPEN);
        draft.setCurrentAggregateId("aggregate");
        return draft;
    }
}
