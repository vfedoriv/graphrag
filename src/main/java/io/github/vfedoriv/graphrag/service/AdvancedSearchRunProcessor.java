package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService.AdvancedSearchSettings;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface AdvancedSearchRunProcessor {
    ProcessingResult process(Context context);

    record Context(
        String knowledgeBaseId,
        String query,
        int maximumEvidence,
        boolean includeEvidenceText,
        Instant deadline,
        AdvancedSearchSettings settings,
        BooleanSupplier cancelled,
        Consumer<AdvancedSearchRunStage> stageChanged
    ) { }

    record ProcessingResult(JsonNode payload, int evidenceCount, List<Result> attempts, int successfulBranches) {
        public ProcessingResult { attempts = attempts == null ? List.of() : List.copyOf(attempts); }
    }
}
