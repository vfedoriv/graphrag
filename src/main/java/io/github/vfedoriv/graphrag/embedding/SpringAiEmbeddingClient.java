package io.github.vfedoriv.graphrag.embedding;

import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringAiEmbeddingClient implements EmbeddingClient {
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AiObservationService aiObservationService;

    public SpringAiEmbeddingClient(
        ObjectProvider<EmbeddingModel> embeddingModelProvider,
        AiObservationService aiObservationService
    ) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.aiObservationService = aiObservationService;
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
        long startNanos = System.nanoTime();
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            log.error("Embedding model bean is missing: chunks={}", texts == null ? 0 : texts.size());
            throw new IllegalStateException("EmbeddingModel bean is not available in application context");
        }
        log.info("Embedding client resolved embeddingModelClass={}", embeddingModel.getClass().getName());
        log.info("Embedding requested: chunks={}", texts.size());
        Map<String, String> attributes = new HashMap<>(aiObservationService.langfuseInputAttributes(
            "Embedding batch with " + texts.size() + " chunks"
        ));
        attributes.put("ai.embedding.batch_size", String.valueOf(texts.size()));
        try (AiModelCallObservation observation = aiObservationService.startEmbeddingModelCall(
            AiObservationService.WORKFLOW_DOCUMENT_PROCESSING,
            attributes
        )) {
            try {
                EmbeddingResponse response = embeddingModel.embedForResponse(texts);
                List<List<Double>> result = new ArrayList<>(response.getResults().size());
                for (Embedding output : response.getResults()) {
                    float[] vector = output.getOutput();
                    List<Double> item = new ArrayList<>(vector.length);
                    for (float v : vector) {
                        item.add((double) v);
                    }
                    result.add(item);
                }
                int firstVectorSize = result.isEmpty() ? 0 : result.getFirst().size();
                log.info(
                    "Embedding response received: vectors={}, firstVectorSize={}, elapsedMs={}",
                    result.size(),
                    firstVectorSize,
                    LogSanitizer.elapsedMillis(startNanos)
                );
                observation.highCardinalityAttributes(aiObservationService.langfuseOutputAttributes(
                    "Embedding response with " + result.size() + " vectors and first vector size " + firstVectorSize
                ));
                observation.success(AiTokenUsage.fromResponse(response));
                return result;
            } catch (RuntimeException ex) {
                observation.error(ex);
                throw ex;
            }
        }
    }
}
