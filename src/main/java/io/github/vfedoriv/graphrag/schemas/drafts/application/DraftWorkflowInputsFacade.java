package io.github.vfedoriv.graphrag.schemas.drafts.application;

import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.ProjectionResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftContributors;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftReviewInputs;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftConflictRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceResultRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DraftWorkflowInputsFacade implements DraftAdmissions, DraftContributors, DraftReviewInputs {
    private final SchemaDraftLifecycleService lifecycle;
    private final SchemaDraftReviewService review;
    private final SchemaDraftAggregateRevisionRepository aggregates;
    private final SchemaDraftConflictRepository conflicts;
    private final SchemaDraftDecisionRepository decisions;
    private final SchemaDraftSourceResultRepository results;
    private final SchemaDraftJsonSupport json;
    private final SchemaDraftGuidanceMapper guidance;

    public DraftWorkflowInputsFacade(SchemaDraftLifecycleService lifecycle, SchemaDraftReviewService review,
        SchemaDraftAggregateRevisionRepository aggregates, SchemaDraftConflictRepository conflicts,
        SchemaDraftDecisionRepository decisions, SchemaDraftSourceResultRepository results, SchemaDraftJsonSupport json, SchemaDraftGuidanceMapper guidance) {
        this.lifecycle = lifecycle;
        this.review = review;
        this.aggregates = aggregates;
        this.conflicts = conflicts;
        this.decisions = decisions;
        this.results = results;
        this.json = json;
        this.guidance = guidance;
    }

    @RelationalTransactional(readOnly = true)
    @Override public Draft requireOwned(String knowledgeBaseId, String draftId) {
        return snapshot(lifecycle.requireOwned(knowledgeBaseId, draftId));
    }

    @RelationalTransactional(readOnly = true)
    @Override public Draft requireMutable(String knowledgeBaseId, String draftId, long revision) {
        return snapshot(lifecycle.requireMutable(knowledgeBaseId, draftId, revision));
    }

    @RelationalTransactional(readOnly = true)
    @Override public Contributors contributors(String knowledgeBaseId, String draftId) {
        lifecycle.requireOwned(knowledgeBaseId, draftId);
        return new Contributors(Set.copyOf(results.findContributingSourceSha256s(draftId)),
            Set.copyOf(results.findHistoricalContributingDocumentIds(draftId)));
    }

    @RelationalTransactional(readOnly = true)
    @Override public Projection projection(String knowledgeBaseId, String draftId) {
        lifecycle.requireOwned(knowledgeBaseId, draftId);
        ProjectionResponse projection = review.projection(knowledgeBaseId, draftId);
        return new Projection(projection.aggregateRevisionId(), projection.draftRevision(), json.canonical(projection.schema()));
    }

    @RelationalTransactional(readOnly = true)
    @Override public String canonicalDecisions(String knowledgeBaseId, String draftId) {
        lifecycle.requireOwned(knowledgeBaseId, draftId);
        return json.canonical(review.decisions(knowledgeBaseId, draftId));
    }

    @RelationalTransactional(readOnly = true)
    @Override public Aggregate aggregate(String knowledgeBaseId, String draftId, String aggregateRevisionId) {
        lifecycle.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftAggregateRevisionNode aggregate = aggregates.findById(aggregateRevisionId)
            .filter(value -> draftId.equals(value.getDraftId()))
            .orElseThrow(() -> new NotFoundException("Schema draft aggregate not found: " + aggregateRevisionId));
        Set<String> identities = decisions.findByDraftIdOrderBySequenceAsc(draftId).stream()
            .map(SchemaDraftDecisionNode::getCandidateIdentity).collect(Collectors.toSet());
        List<Conflict> values = conflicts.findByAggregateRevision(draftId, aggregateRevisionId).stream()
            .map(value -> new Conflict(value.getId(), value.getType().name(), value.getCoordinate(), value.isResolved())).toList();
        return new Aggregate(aggregate.getCandidatesJson(), identities, values);
    }

    @RelationalTransactional(readOnly = true)
    @Override public List<String> intendedQuestions(String guidanceJson) {
        return List.copyOf(guidance.read(guidanceJson).guidance().intendedQuestions());
    }

    private Draft snapshot(SchemaDraftNode draft) {
        return new Draft(draft.getId(), draft.getKnowledgeBaseId(), draft.getRevision(), draft.getPersistenceVersion(),
            draft.getTargetName(), draft.getTargetVersion(), draft.getCurrentAggregateId(), draft.getGuidanceJson(),
            draft.getStatus() == io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStatus.OPEN, draft.getPublicationSchemaId(), draft.getPublicationContentHash());
    }
}
