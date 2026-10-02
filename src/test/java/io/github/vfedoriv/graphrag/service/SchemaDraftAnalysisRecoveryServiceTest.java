package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftAnalysisRecoveryService;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaDraftAnalysisRecoveryServiceTest {

    @Test
    void closesLegacyAndPolicyBearingRunsWithoutResumingCapturedRuntimeState() {
        SchemaDraftAnalysisRunRepository runRepository = mock(SchemaDraftAnalysisRunRepository.class);
        SchemaDraftRepository draftRepository = mock(SchemaDraftRepository.class);
        SchemaDraftAnalysisRunNode legacy = running("legacy", "draft-legacy");
        SchemaDraftAnalysisRunNode policyBearing = running("policy", "draft-policy");
        policyBearing.setDiscoveryMaxConcurrency(3);
        policyBearing.setDiscoverySourceTimeoutMillis(1_000L);
        policyBearing.setDiscoveryRequestTimeoutMillis(5_000L);
        when(runRepository.findByStatus(SchemaDraftAnalysisStatus.RUNNING))
            .thenReturn(List.of(legacy, policyBearing));

        new SchemaDraftAnalysisRecoveryService(runRepository, draftRepository).run(null);

        assertThat(List.of(legacy, policyBearing)).allSatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo(SchemaDraftAnalysisStatus.FAILED);
            assertThat(run.getFailureCategory()).isEqualTo("APPLICATION_RESTART");
            assertThat(run.isRetryable()).isTrue();
            assertThat(run.getCompletedAt()).isNotNull();
            verify(runRepository).save(run);
            verify(draftRepository).releaseAnalysis(run.getDraftId(), run.getId());
        });
    }

    private SchemaDraftAnalysisRunNode running(String id, String draftId) {
        SchemaDraftAnalysisRunNode run = new SchemaDraftAnalysisRunNode();
        run.setId(id);
        run.setDraftId(draftId);
        run.setStatus(SchemaDraftAnalysisStatus.RUNNING);
        return run;
    }
}
