package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class LangChain4jSchemaGenerationServiceTest {

    @Test
    void inferSchema_preservesNodeDescriptionsAndProperties_andEdgeDescriptions() {
        LangChain4jSchemaGenerationService service = new LangChain4jSchemaGenerationService(null);
        GraphNode contract = GraphNode.from("c1", "contract", Map.of(
            "description", "A legal agreement",
            "contractId", "C-001",
            "amount", "1250.50",
            "signed", "true",
            "effectiveDate", "2026-05-09"
        ));
        GraphNode party = GraphNode.from("p1", "party", Map.of(
            "description", "A contract participant",
            "name", "Acme Corp"
        ));
        GraphEdge edge = GraphEdge.from(contract, party, "has party", Map.of(
            "description", "Contract references participant",
            "role", "seller"
        ));

        GraphDocument graphDocument = GraphDocument.from(Set.of(contract, party), Set.of(edge), Document.from("text"));
        SchemaDocument schema = service.inferSchema("generated", 1, "desc", graphDocument);

        SchemaDocument.NodeDefinition contractNode = schema.nodes().stream()
            .filter(node -> node.label().equals("Contract"))
            .findFirst()
            .orElseThrow();
        assertThat(contractNode.description()).isEqualTo("A legal agreement");
        assertThat(contractNode.properties()).extracting(SchemaDocument.PropertyDefinition::name)
            .containsExactly("amount", "contractId", "effectiveDate", "signed");
        assertThat(contractNode.properties()).extracting(SchemaDocument.PropertyDefinition::type)
            .containsExactly("number", "string", "date", "boolean");

        SchemaDocument.RelationshipDefinition relationship = schema.relationships().getFirst();
        assertThat(relationship.type()).isEqualTo("HAS_PARTY");
        assertThat(relationship.from()).isEqualTo("Contract");
        assertThat(relationship.to()).isEqualTo("Party");
        assertThat(relationship.description()).isEqualTo("Contract references participant");
        assertThat(relationship.properties()).extracting(SchemaDocument.PropertyDefinition::name)
            .containsExactly("role");
    }

    @Test
    void inferSchema_handlesMissingOptionalMetadata() {
        LangChain4jSchemaGenerationService service = new LangChain4jSchemaGenerationService(null);
        GraphNode source = GraphNode.from("n1", "NodeType", Map.of());
        GraphNode target = GraphNode.from("n2", "AnotherType", Map.of());
        GraphEdge edge = GraphEdge.from(source, target, "relates_to", Map.of());
        GraphDocument graphDocument = GraphDocument.from(Set.of(source, target), Set.of(edge), Document.from("text"));

        SchemaDocument schema = service.inferSchema("generated", 1, "desc", graphDocument);

        assertThat(schema.nodes()).hasSize(2);
        assertThat(schema.nodes()).allSatisfy(node -> {
            assertThat(node.description()).isNull();
            assertThat(node.properties()).isEmpty();
        });
        assertThat(schema.relationships()).hasSize(1);
        assertThat(schema.relationships().getFirst().description()).isNull();
        assertThat(schema.relationships().getFirst().properties()).isEmpty();
    }

    @Test
    void generateJson_usesTransformedPropertiesInSchemaOutput() {
        String modelJson = """
            [
              {
                "head": "Contract A",
                "head_type": "Contract",
                "head_properties": {"description": ["A legal agreement"], "contractId": ["C-001"]},
                "relation": "HAS_PARTY",
                "relation_properties": {"description": ["Contract has participant"], "role": ["seller"]},
                "tail": "Acme Corp",
                "tail_type": "Party",
                "tail_properties": {"name": ["Acme Corp"], "description": ["A participant"]}
              }
            ]
            """;

        org.springframework.ai.chat.model.ChatModel springModel = new org.springframework.ai.chat.model.ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(modelJson))));
            }
        };

        ObjectProvider<org.springframework.ai.chat.model.ChatModel> provider = new ObjectProvider<>() {
            @Override
            public Stream<org.springframework.ai.chat.model.ChatModel> stream() {
                return Stream.of(springModel);
            }
        };

        LangChain4jSchemaGenerationService service = new LangChain4jSchemaGenerationService(provider);
        String yaml = service.generateJson("generated", 1, "desc", "source text", "[]");

        SchemaDocument schema = new SchemaParser().parse(yaml);
        SchemaDocument.NodeDefinition contract = schema.nodes().stream()
            .filter(node -> node.label().equals("Contract"))
            .findFirst()
            .orElseThrow();
        assertThat(contract.description()).isEqualTo("A legal agreement");
        assertThat(contract.properties()).extracting(SchemaDocument.PropertyDefinition::name).contains("contractId");

        SchemaDocument.RelationshipDefinition rel = schema.relationships().getFirst();
        assertThat(rel.description()).isEqualTo("Contract has participant");
        assertThat(rel.properties()).extracting(SchemaDocument.PropertyDefinition::name).contains("role");
    }

}
