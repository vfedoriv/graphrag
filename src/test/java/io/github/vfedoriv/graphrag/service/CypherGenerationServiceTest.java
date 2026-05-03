package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.query.CypherGenerationClient;
import io.github.vfedoriv.graphrag.query.GeneratedCypher;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class CypherGenerationServiceTest {

    @Test
    void mapsGeneratedQueryAndValidation() {
        KnowledgeBaseRepository kbRepo = Mockito.mock(KnowledgeBaseRepository.class);
        SchemaDefinitionRepository schemaRepo = Mockito.mock(SchemaDefinitionRepository.class);
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
        };

        KnowledgeBaseNode kb = new KnowledgeBaseNode();
        kb.setId("kb-1");
        kb.setActiveSchemaId("schema-1");
        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-1");
        schema.setContent("""
            name: contracts
            version: 1
            nodes:
              - label: Contract
                key: contractId
                properties:
                  - name: contractId
                    type: string
            relationships: []
            """);
        when(kbRepo.findById("kb-1")).thenReturn(Optional.of(kb));
        when(schemaRepo.findById("schema-1")).thenReturn(Optional.of(schema));
        when(validationService.validate(Mockito.any(io.github.vfedoriv.graphrag.schema.SchemaDocument.class), Mockito.anyString(), Mockito.anyMap())).thenReturn(
            new QueryValidationResult(true, "MATCH (n:Contract) RETURN n LIMIT $__limit", Map.of("__limit", 200), List.of())
        );

        CypherGenerationService service = new CypherGenerationService(
            props(),
            kbRepo,
            schemaRepo,
            new SchemaParser(),
            provider,
            validationService
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
            new AppProperties.Query(200, 15, true, List.of("CREATE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
