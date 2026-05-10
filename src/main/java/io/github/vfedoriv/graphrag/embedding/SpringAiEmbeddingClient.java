package io.github.vfedoriv.graphrag.embedding;

import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringAiEmbeddingClient implements EmbeddingClient {
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public SpringAiEmbeddingClient(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
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
        var response = embeddingModel.embedForResponse(texts);
        List<List<Double>> result = new ArrayList<>(response.getResults().size());
        for (var output : response.getResults()) {
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
        return result;
    }
}
