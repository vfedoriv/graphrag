package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import org.springframework.stereotype.Service;

@Service
public class SchemaDraftAnalysisRetryEligibilityService {
    private final SchemaDraftAnalysisRunRepository runRepository;
    private final SchemaDraftSourceRepository sourceRepository;

    public SchemaDraftAnalysisRetryEligibilityService(
        SchemaDraftAnalysisRunRepository runRepository,
        SchemaDraftSourceRepository sourceRepository
    ) {
        this.runRepository = runRepository;
        this.sourceRepository = sourceRepository;
    }

    public EligibilityInputs inputs(SchemaDraftNode draft) {
        boolean hasActiveSources = sourceRepository.existsByDraftIdAndStatus(
            draft.getId(), SchemaDraftSourceStatus.ACTIVE);
        boolean hasRunningAnalysis = runRepository.findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
            draft.getId(), SchemaDraftAnalysisStatus.RUNNING).isPresent();
        return new EligibilityInputs(draft.getStatus() == SchemaDraftStatus.OPEN,
            hasActiveSources, hasRunningAnalysis);
    }

    public EligibilityDecision decide(SchemaDraftAnalysisRunNode run, EligibilityInputs inputs) {
        if (run.getStatus() == SchemaDraftAnalysisStatus.RUNNING) {
            return EligibilityDecision.ineligible(IneligibilityReason.RUN_NOT_TERMINAL);
        }
        if (!inputs.draftOpen()) {
            return EligibilityDecision.ineligible(IneligibilityReason.DRAFT_NOT_OPEN);
        }
        if (!inputs.hasActiveSources()) {
            return EligibilityDecision.ineligible(IneligibilityReason.NO_ACTIVE_SOURCES);
        }
        if (inputs.hasRunningAnalysis()) {
            return EligibilityDecision.ineligible(IneligibilityReason.ANALYSIS_RUNNING);
        }
        return EligibilityDecision.eligible();
    }

    public record EligibilityInputs(
        boolean draftOpen,
        boolean hasActiveSources,
        boolean hasRunningAnalysis
    ) { }

    public record EligibilityDecision(boolean canRetry, IneligibilityReason reason) {
        private static EligibilityDecision eligible() {
            return new EligibilityDecision(true, null);
        }

        private static EligibilityDecision ineligible(IneligibilityReason reason) {
            return new EligibilityDecision(false, reason);
        }
    }

    public enum IneligibilityReason {
        RUN_NOT_TERMINAL,
        DRAFT_NOT_OPEN,
        NO_ACTIVE_SOURCES,
        ANALYSIS_RUNNING
    }
}
