package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentDryExtraction;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.documents.ports.GraphExtractionClient;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DocumentDryExtractionFacade implements DocumentDryExtraction {
    private final ObjectProvider<GraphExtractionClient> clients;
    private final GraphExtractionValidationService validation;

    public DocumentDryExtractionFacade(ObjectProvider<GraphExtractionClient> clients, GraphExtractionValidationService validation) {
        this.clients = clients;
        this.validation = validation;
    }

    @Override public boolean available() { return resolveClient() != null; }

    @Override public Observation extract(SchemaDocument schema, String chunk, String aiProfileId) {
        GraphExtractionClient client = resolveClient();
        if (client == null) throw new IllegalStateException("AI extraction client is unavailable");
        GraphExtractionResult raw = AiProfileContext.withProfile(aiProfileId, () -> client.extract(schema, chunk));
        GraphExtractionResult validated = validation.validate(raw, schema);
        return new Observation(map(raw), map(validated));
    }

    private GraphExtractionClient resolveClient() {
        List<GraphExtractionClient> available = clients.orderedStream().toList();
        if (available.isEmpty()) return null;
        if (available.size() == 1) return available.getFirst();
        return available.stream().filter(client -> !client.getClass().getName().contains("SpringAi"))
            .findFirst().orElse(available.getFirst());
    }

    private Result map(GraphExtractionResult result) {
        return new Result(result.nodes().stream().map(node -> new Node(node.label(), node.properties(), node.confidence())).toList(),
            result.relationships().stream().map(relationship -> new Relationship(relationship.type(), relationship.fromLabel(),
                relationship.fromKey(), relationship.toLabel(), relationship.toKey(), relationship.properties(), relationship.confidence())).toList());
    }
}
