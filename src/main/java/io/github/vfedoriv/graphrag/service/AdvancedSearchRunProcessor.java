package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
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
        String activeAiProfileId,
        String schemaDefinitionId,
        String schemaContentHash,
        String schemaSnapshotJson,
        int maximumEvidence,
        boolean includeEvidenceText,
        Instant deadline,
        AdvancedSearchSettings settings,
        BooleanSupplier cancelled,
        Consumer<AdvancedSearchRunStage> stageChanged
    ) { }

    record Attempt(
        int roundNumber,
        String subqueryId,
        String retriever,
        String status,
        int candidateCount,
        long latencyMs,
        String failureCategory
    ) { }

    record ProcessingResult(
        JsonNode payload,
        int evidenceCount,
        List<Attempt> attempts,
        int successfulBranches,
        int totalBranches,
        boolean answered,
        String answerFailureCategory
    ) {
        public ProcessingResult { attempts = attempts == null ? List.of() : List.copyOf(attempts); }

        public ProcessingResult(
            JsonNode payload,
            int evidenceCount,
            List<Attempt> attempts,
            int successfulBranches,
            int totalBranches
        ) {
            this(payload, evidenceCount, attempts, successfulBranches, totalBranches, true, null);
        }
    }
}
