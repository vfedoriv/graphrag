package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchRunRepository;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Component
public class AdvancedSearchRunMaintenance implements ApplicationRunner {
    private final AdvancedSearchRunRepository repository;
    private final AdvancedSearchRunLifecycle lifecycle;
    private final RuntimeSettingsService settingsService;
    public AdvancedSearchRunMaintenance(
        AdvancedSearchRunRepository repository,
        AdvancedSearchRunLifecycle lifecycle,
        RuntimeSettingsService settingsService
    ) { this.repository = repository; this.lifecycle = lifecycle; this.settingsService = settingsService; }

    @Override
    @RelationalTransactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        for (AdvancedSearchRunNode run : repository.findActive()) {
            lifecycle.terminal(run, AdvancedSearchRunStatus.INTERRUPTED, "APPLICATION_RESTART", 0, now,
                settingsService.advancedSearch().retention());
            repository.save(run);
        }
    }

    @Scheduled(fixedDelayString = "${app.advanced-search.cleanup-delay-ms:300000}")
    @RelationalTransactional
    public int cleanup() {
        return repository.deleteExpired(Instant.now(), settingsService.advancedSearch().cleanupBatchSize());
    }
}
