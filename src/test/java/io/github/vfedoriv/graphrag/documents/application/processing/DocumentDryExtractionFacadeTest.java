package io.github.vfedoriv.graphrag.documents.application.processing;

import static org.assertj.core.api.Assertions.*;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentDryExtraction;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.documents.ports.GraphExtractionClient;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class DocumentDryExtractionFacadeTest {
    private final GraphExtractionValidationService validator = new GraphExtractionValidationService(TestRuntimeSettings.from(
        new AppProperties(new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("http://localhost", "", "embedding", 3, "chat"),
            new AppProperties.Storage(Path.of("var/documents")), new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE")), new AppProperties.Extraction(40, 80, 2))));

    @Test void retainsRawViolationsAndValidatedValuesAndUsesRequestedProfile() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        Map<String, Object> properties = new LinkedHashMap<>(Map.of("id", "p1", "age", "wrong", "nested", new ArrayList<>(List.of("a"))));
        GraphExtractionClient client = (schema, chunk) -> {
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("profile");
            assertThat(chunk).isEqualTo("held-out");
            return new GraphExtractionResult(List.of(new GraphExtractionResult.ExtractedNode("Person", properties, .7),
                new GraphExtractionResult.ExtractedNode("Unknown", Map.of("id", "u"), .5)),
                List.of(new GraphExtractionResult.ExtractedRelationship("INVALID", "Person", Map.of("id", "p1"),
                    "Person", Map.of("id", "p2"), Map.of("rank", 2), .3)));
        };
        beans.registerSingleton("client", client);
        DocumentDryExtractionFacade facade = new DocumentDryExtractionFacade(beans.getBeanProvider(GraphExtractionClient.class), validator);
        DocumentDryExtraction.Observation observation = facade.extract(schema(), "held-out", "profile");
        properties.put("id", "changed");
        ((List<String>) properties.get("nested")).clear();
        assertThat(observation.raw().nodes()).extracting(DocumentDryExtraction.Node::label).containsExactly("Person", "Unknown");
        assertThat(observation.validated().nodes()).extracting(DocumentDryExtraction.Node::label).containsExactly("Person");
        assertThat(observation.validated().relationships()).isEmpty();
        assertThat(observation.raw().nodes().getFirst().properties()).containsEntry("id", "p1").containsEntry("age", "wrong");
        assertThat(observation.raw().nodes().getFirst().properties().get("nested")).isEqualTo(List.of("a"));
        assertThat(observation.raw().relationships().getFirst().properties()).containsEntry("rank", 2);
        assertThatThrownBy(() -> observation.raw().nodes().getFirst().properties().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<?>) observation.raw().nodes().getFirst().properties().get("nested")).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(AiProfileContext.activeProfileId()).isNull();
    }

    @Test void reportsNoClientAndPropagatesModelFailureWithoutLeavingProfileContext() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        DocumentDryExtractionFacade facade = new DocumentDryExtractionFacade(beans.getBeanProvider(GraphExtractionClient.class), validator);
        assertThat(facade.available()).isFalse();
        beans.registerSingleton("failing", (GraphExtractionClient) (schema, text) -> { throw new IllegalStateException("model failure"); });
        assertThat(facade.available()).isTrue();
        assertThatThrownBy(() -> facade.extract(schema(), "held-out", "profile")).isInstanceOf(IllegalStateException.class);
        assertThat(AiProfileContext.activeProfileId()).isNull();
    }

    @Test void prefersCustomClientWhenSpringAiAndCustomClientsAreBothRegistered() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        io.github.vfedoriv.graphrag.documents.adapters.model.SpringAiGraphExtractionClient springAi =
            org.mockito.Mockito.mock(io.github.vfedoriv.graphrag.documents.adapters.model.SpringAiGraphExtractionClient.class);
        GraphExtractionClient custom = org.mockito.Mockito.mock(GraphExtractionClient.class);
        GraphExtractionResult empty = new GraphExtractionResult(List.of(), List.of());
        org.mockito.Mockito.when(custom.extract(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("chunk"))).thenReturn(empty);
        beans.registerSingleton("springAi", springAi);
        beans.registerSingleton("custom", custom);
        DocumentDryExtractionFacade facade = new DocumentDryExtractionFacade(beans.getBeanProvider(GraphExtractionClient.class), validator);
        assertThat(facade.available()).isTrue();
        assertThat(facade.extract(schema(), "chunk", "profile").raw().nodes()).isEmpty();
        org.mockito.Mockito.verify(custom).extract(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("chunk"));
        org.mockito.Mockito.verifyNoInteractions(springAi);
    }

    private SchemaDocument schema() {
        return new SchemaDocument("people", 1, null, List.of(new SchemaDocument.NodeDefinition("Person", null, List.of("id"),
            List.of(new SchemaDocument.PropertyDefinition("id", "STRING", true), new SchemaDocument.PropertyDefinition("age", "INTEGER", false)))),
            List.of(), List.of(), List.of());
    }
}
