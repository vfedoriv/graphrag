package io.github.vfedoriv.graphrag.schemas.drafts.contracts;

import java.util.List;
import java.util.Set;

/** Immutable canonical review inputs; interpretation belongs to the consuming workflow. */
public interface DraftReviewInputs {
    Projection projection(String knowledgeBaseId, String draftId);
    String canonicalDecisions(String knowledgeBaseId, String draftId);
    Aggregate aggregate(String knowledgeBaseId, String draftId, String aggregateRevisionId);
    List<String> intendedQuestions(String guidanceJson);

    record Projection(String aggregateRevisionId, long draftRevision, String schemaJson) { }
    record Conflict(String id, String type, String coordinate, boolean resolved) { }
    record Aggregate(String candidatesJson, Set<String> decidedIdentities, List<Conflict> conflicts) {
        public Aggregate {
            decidedIdentities = Set.copyOf(decidedIdentities);
            conflicts = List.copyOf(conflicts);
        }
    }
}
