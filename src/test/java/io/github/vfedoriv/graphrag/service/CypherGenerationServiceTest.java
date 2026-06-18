package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.query.CypherGenerationClient;
import io.github.vfedoriv.graphrag.query.GeneratedCypher;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class CypherGenerationServiceTest {

    @Test
    void mapsGeneratedQueryAndValidation() {
        ActiveSchemaResolver activeSchemaResolver = Mockito.mock(ActiveSchemaResolver.class);
        CypherValidationService validationService = Mockito.mock(CypherValidationService.class);
        CypherGenerationClient generationClient = (schema, prompt, maxRows) -> new GeneratedCypher(
            "MATCH (n:Contract) RETURN n",
            "Return contracts",
            Map.of("title", "MSA")
        );
        ObjectProvider<CypherGenerationClient> provider = new ObjectProvider<>() {
            @Override
            public CypherGenerationClient getObject(Object... args) {
                return generationClient;
            }

            @Override
            public CypherGenerationClient getIfAvailable() {
                return generationClient;
            }

            @Override
            public CypherGenerationClient getIfUnique() {
                return generationClient;
            }

            @Override
            public CypherGenerationClient getObject() {
                return generationClient;
            }

            @Override
            public Stream<CypherGenerationClient> stream() {
                return Stream.of(generationClient);
            }

            @Override
            public Stream<CypherGenerationClient> orderedStream() {
                return Stream.of(generationClient);
            }
        };

        SchemaDefinitionNode schemaDefinition = new SchemaDefinitionNode();
        schemaDefinition.setId("schema-1");
        schemaDefinition.setName("contracts");
        SchemaDocument schema = new SchemaParser().parse("""
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                }
              ],
              "relationships": []
            }
            """);
        when(activeSchemaResolver.resolve("kb-1")).thenReturn(new ActiveSchemaContext("kb-1", "schema-1", schemaDefinition, schema));
        KnowledgeBaseService knowledgeBaseService = org.mockito.Mockito.mock(KnowledgeBaseService.class);
        when(knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(profile());
        when(validationService.validate(Mockito.any(io.github.vfedoriv.graphrag.schema.SchemaDocument.class), Mockito.anyString(), Mockito.anyMap())).thenReturn(
            new QueryValidationResult(true, "MATCH (n:Contract) RETURN n LIMIT $__limit", Map.of("__limit", 200), List.of())
        );

        CypherGenerationService service = new CypherGenerationService(
            TestRuntimeSettings.from(props()),
            activeSchemaResolver,
            provider,
            validationService,
            TestAiObservationService.noop(),
            knowledgeBaseService
        );

        GeneratedQueryResponse response = service.generate("kb-1", "list contracts");

        assertThat(response.cypher()).isEqualTo("MATCH (n:Contract) RETURN n");
        assertThat(response.explanation()).isEqualTo("Return contracts");
        assertThat(response.parameters()).containsEntry("title", "MSA");
        assertThat(response.validation().valid()).isTrue();
        assertThat(response.validation().maxRows()).isEqualTo(200);
        assertThat(response.validation().timeoutSeconds()).isEqualTo(15);
    }

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(AiProfileService.DEFAULT_PROFILE_ID);
        return profile;
    }
}
