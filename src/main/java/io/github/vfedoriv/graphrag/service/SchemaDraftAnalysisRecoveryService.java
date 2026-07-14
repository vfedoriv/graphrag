package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SchemaDraftAnalysisRecoveryService implements ApplicationRunner {
    private final SchemaDraftAnalysisRunRepository runRepository;
    private final SchemaDraftRepository draftRepository;

    public SchemaDraftAnalysisRecoveryService(
        SchemaDraftAnalysisRunRepository runRepository, SchemaDraftRepository draftRepository
    ) {
        this.runRepository = runRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SchemaDraftAnalysisRunNode> interrupted = runRepository.findByStatus(SchemaDraftAnalysisStatus.RUNNING);
        for (SchemaDraftAnalysisRunNode run : interrupted) {
            run.setStatus(SchemaDraftAnalysisStatus.FAILED);
            run.setFailureCategory("APPLICATION_RESTART");
            run.setRetryable(true);
            run.setCompletedAt(Instant.now());
            runRepository.save(run);
            draftRepository.releaseAnalysis(run.getDraftId(), run.getId());
        }
    }
}
