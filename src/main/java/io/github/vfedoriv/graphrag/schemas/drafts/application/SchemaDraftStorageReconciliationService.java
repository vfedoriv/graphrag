package io.github.vfedoriv.graphrag.schemas.drafts.application;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationType;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftStorageMutationRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftBinaryStorage;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@Slf4j
public class SchemaDraftStorageReconciliationService implements ApplicationRunner {
    private static final int MAX_RETRIES = 10;
    private static final Duration RETENTION = Duration.ofDays(7);
    private final SchemaDraftStorageMutationRepository mutationRepository;
    private final SchemaDraftSourceRepository sourceRepository;
    private final SchemaDraftStorageMutationService mutationService;
    private final DraftBinaryStorage storageService;

    public SchemaDraftStorageReconciliationService(
        SchemaDraftStorageMutationRepository mutationRepository,
        SchemaDraftSourceRepository sourceRepository,
        SchemaDraftStorageMutationService mutationService,
        DraftBinaryStorage storageService
    ) {
        this.mutationRepository = mutationRepository;
        this.sourceRepository = sourceRepository;
        this.mutationService = mutationService;
        this.storageService = storageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcile();
    }

    @Scheduled(fixedDelay = 300_000L)
    public void reconcile() {
        List<SchemaDraftStorageMutationNode> pending = mutationRepository
            .findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState.PENDING);
        pending.forEach(this::reconcileOne);
        mutationRepository.deleteCompletedBefore(Instant.now().minus(RETENTION));
    }

    private void reconcileOne(SchemaDraftStorageMutationNode mutation) {
        try {
            if (mutation.getType() == SchemaDraftStorageMutationType.STORE) {
                reconcileStore(mutation);
            } else {
                deleteIfPresent(mutation.getContentUri());
                mutationService.complete(mutation.getId());
            }
        } catch (Exception exception) {
            mutationService.failure(mutation.getId(), exception);
            log.warn("Draft storage reconciliation failed: mutationId={}, draftId={}, sourceId={}, retryCount={}, exceptionType={}",
                mutation.getId(), mutation.getDraftId(), mutation.getSourceId(), mutation.getRetryCount() + 1,
                exception.getClass().getSimpleName());
            if (mutation.getRetryCount() + 1 >= MAX_RETRIES) {
                log.error("Draft storage mutation exhausted retries: mutationId={}, draftId={}, sourceId={}",
                    mutation.getId(), mutation.getDraftId(), mutation.getSourceId());
            }
        }
    }

    private void reconcileStore(SchemaDraftStorageMutationNode mutation) throws Exception {
        SchemaDraftSourceNode source = sourceRepository.findById(mutation.getSourceId()).orElse(null);
        if (source != null && mutation.getContentUri() != null
            && mutation.getContentUri().equals(source.getContentUri())) {
            mutationService.complete(mutation.getId());
            return;
        }
        deleteIfPresent(mutation.getContentUri());
        mutationService.compensate(mutation.getId());
    }

    private void deleteIfPresent(String uriValue) throws Exception {
        if (uriValue == null || uriValue.isBlank()) {
            return;
        }
        URI uri = URI.create(uriValue);
        if (storageService.exists(uri)) {
            storageService.delete(uri);
        }
    }
}
