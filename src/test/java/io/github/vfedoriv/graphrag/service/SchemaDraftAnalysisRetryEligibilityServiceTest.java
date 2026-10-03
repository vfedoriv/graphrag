package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftAnalysisRetryEligibilityService;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import org.junit.jupiter.api.Test;

class SchemaDraftAnalysisRetryEligibilityServiceTest {
    private final SchemaDraftAnalysisRetryEligibilityService service =
        new SchemaDraftAnalysisRetryEligibilityService(
            mock(SchemaDraftAnalysisRunRepository.class),
            mock(SchemaDraftSourceRepository.class));

    @Test
    void permitsCompletedRetryableAndPermanentFailuresWhenResourceStateAllowsRetry() {
        assertEligible(run(SchemaDraftAnalysisStatus.COMPLETED, false));
        assertEligible(run(SchemaDraftAnalysisStatus.PARTIAL, true));
        assertEligible(run(SchemaDraftAnalysisStatus.FAILED, false));
    }

    @Test
    void rejectsRunningAnalysis() {
        assertIneligible(run(SchemaDraftAnalysisStatus.RUNNING, false),
            new SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs(true, true, true),
            SchemaDraftAnalysisRetryEligibilityService.IneligibilityReason.RUN_NOT_TERMINAL);
    }

    @Test
    void rejectsClosedDraft() {
        assertIneligible(run(SchemaDraftAnalysisStatus.COMPLETED, false),
            new SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs(false, true, false),
            SchemaDraftAnalysisRetryEligibilityService.IneligibilityReason.DRAFT_NOT_OPEN);
    }

    @Test
    void rejectsDraftWithoutActiveSources() {
        assertIneligible(run(SchemaDraftAnalysisStatus.FAILED, true),
            new SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs(true, false, false),
            SchemaDraftAnalysisRetryEligibilityService.IneligibilityReason.NO_ACTIVE_SOURCES);
    }

    @Test
    void rejectsTerminalRunWhileAnotherAnalysisIsRunning() {
        assertIneligible(run(SchemaDraftAnalysisStatus.PARTIAL, true),
            new SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs(true, true, true),
            SchemaDraftAnalysisRetryEligibilityService.IneligibilityReason.ANALYSIS_RUNNING);
    }

    private void assertEligible(SchemaDraftAnalysisRunNode run) {
        SchemaDraftAnalysisRetryEligibilityService.EligibilityDecision decision = service.decide(
            run, new SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs(true, true, false));

        assertThat(decision.canRetry()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    private void assertIneligible(
        SchemaDraftAnalysisRunNode run,
        SchemaDraftAnalysisRetryEligibilityService.EligibilityInputs inputs,
        SchemaDraftAnalysisRetryEligibilityService.IneligibilityReason reason
    ) {
        SchemaDraftAnalysisRetryEligibilityService.EligibilityDecision decision = service.decide(run, inputs);

        assertThat(decision.canRetry()).isFalse();
        assertThat(decision.reason()).isEqualTo(reason);
    }

    private SchemaDraftAnalysisRunNode run(SchemaDraftAnalysisStatus status, boolean retryable) {
        SchemaDraftAnalysisRunNode run = new SchemaDraftAnalysisRunNode();
        run.setStatus(status);
        run.setRetryable(retryable);
        return run;
    }
}
