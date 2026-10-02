package io.github.vfedoriv.graphrag.knowledgebase.contracts;

import java.util.List;

public record KnowledgeBaseSchemaFacts(String knowledgeBaseId, String activeSchemaId) {
    public record Association(String schemaId, boolean active) {
    }

    public record Associations(List<Association> values) {
        public Associations {
            values = List.copyOf(values);
        }
    }
}
