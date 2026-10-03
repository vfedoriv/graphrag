package io.github.vfedoriv.graphrag.search.answering.domain;

import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.Answer;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.AnswerStatus;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.Claim;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.ClaimKind;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.Limitation;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchCitationCatalog.Catalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchAnswerValidator {

    public Validation validate(Answer answer, Catalog catalog) {
        List<String> errors = new ArrayList<>();
        if (answer == null) {
            return new Validation(false, List.of("answer.missing"));
        }
        if (answer.version() != AdvancedSearchAnswerContracts.ANSWER_VERSION) {
            errors.add("answer.version.unsupported");
        }
        if (answer.status() == null) {
            errors.add("answer.status.missing");
        }
        if (answer.confidence() == null || answer.confidence().level() == null
            || !Double.isFinite(answer.confidence().score())
            || answer.confidence().score() < 0.0 || answer.confidence().score() > 1.0) {
            errors.add("answer.confidence.invalid");
        }
        validateLimitations(answer.limitations(), errors);
        if (answer.claims().size() > AdvancedSearchAnswerContracts.MAX_CLAIMS) {
            errors.add("answer.claims.limit");
        }
        if (answer.status() == AnswerStatus.ANSWERED) {
            if (!hasText(answer.text())) {
                errors.add("answer.text.missing");
            }
            if (answer.claims().isEmpty()) {
                errors.add("answer.claims.missing");
            }
        } else if (!answer.claims().isEmpty()) {
            errors.add("answer.abstention.claims-not-empty");
        }
        validateClaims(answer.claims(), catalog, errors);
        return new Validation(errors.isEmpty(), List.copyOf(errors));
    }

    private void validateLimitations(List<Limitation> limitations, List<String> errors) {
        if (limitations.size() > AdvancedSearchAnswerContracts.MAX_LIMITATIONS) {
            errors.add("answer.limitations.limit");
        }
        for (Limitation limitation : limitations) {
            if (limitation == null || !hasText(limitation.code()) || !hasText(limitation.description())) {
                errors.add("answer.limitation.invalid");
            }
        }
    }

    private void validateClaims(List<Claim> claims, Catalog catalog, List<String> errors) {
        Set<String> claimIds = new HashSet<>();
        for (Claim claim : claims) {
            if (claim == null || !hasText(claim.id()) || !hasText(claim.text()) || claim.kind() == null) {
                errors.add("answer.claim.invalid");
                continue;
            }
            if (!claimIds.add(claim.id())) {
                errors.add("answer.claim.id-duplicate");
            }
            if (claim.citationIds().isEmpty() || !catalog.citableIds().containsAll(claim.citationIds())) {
                errors.add("answer.claim.citation-unknown");
            }
            if (claim.kind() == ClaimKind.TEXT
                && (!claim.graphFactIds().isEmpty() || !claim.graphEvidenceIds().isEmpty())) {
                errors.add("answer.text-claim.graph-references");
            }
            if (claim.kind() == ClaimKind.GRAPH) {
                validateGraphClaim(claim, catalog, errors);
            }
        }
    }

    private void validateGraphClaim(Claim claim, Catalog catalog, List<String> errors) {
        if (claim.graphFactIds().isEmpty() || claim.graphEvidenceIds().isEmpty()) {
            errors.add("answer.graph-claim.references-missing");
            return;
        }
        if (!catalog.factCitations().keySet().containsAll(claim.graphFactIds())) {
            errors.add("answer.graph-claim.fact-unknown");
        }
        if (!catalog.graphEvidenceIds().containsAll(claim.graphEvidenceIds())) {
            errors.add("answer.graph-claim.evidence-unknown");
        }
        Set<String> allowedCitations = new HashSet<>();
        claim.graphFactIds().forEach(id -> allowedCitations.addAll(
            catalog.factCitations().getOrDefault(id, Set.of())));
        if (allowedCitations.isEmpty() || claim.citationIds().stream().noneMatch(allowedCitations::contains)) {
            errors.add("answer.graph-claim.citation-type-invalid");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Validation(boolean valid, List<String> errors) { }
}
