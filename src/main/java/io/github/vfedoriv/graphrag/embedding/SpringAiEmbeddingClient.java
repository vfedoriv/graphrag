package io.github.vfedoriv.graphrag.embedding;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(EmbeddingModel.class)
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
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
        return result;
    }
}
