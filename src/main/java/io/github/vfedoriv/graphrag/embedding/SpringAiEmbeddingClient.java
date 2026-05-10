package io.github.vfedoriv.graphrag.embedding;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingClient.class);
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public SpringAiEmbeddingClient(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
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
        log.info("Embedding response received: vectors={}, firstVectorSize={}", result.size(), firstVectorSize);
        return result;
    }
}
