package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionAttemptContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionModelAdapter;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionResult;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ConflictCategory;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ResponseStatus;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaAiProfileRepository;
import io.github.vfedoriv.graphrag.repository.AiProfileRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.schemas.discovery.application.SchemaDiscoveryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import({TestcontainersConfiguration.class, SchemaDiscoveryIntegrationTest.DiscoveryAiConfig.class})
@org.springframework.test.context.TestPropertySource(properties = {
    "app.schema-discovery.chunk-characters=1000"
})
@IntegrationTest
class SchemaDiscoveryIntegrationTest {

    @Autowired
    private SchemaDiscoveryService discoveryService;
    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private AiProfileRepository aiProfileRepository;
    @Autowired
    private JpaAiProfileRepository jpaAiProfileRepository;
    @Autowired
    private SchemaDefinitionRepository schemaRepository;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        jpaAiProfileRepository.deleteAll();
        TestDocumentStorage.clean();
        aiProfileRepository.save(profile("discovery-profile", 9));
        knowledgeBaseRepository.save(knowledgeBase("kb-discovery", "discovery-profile"));
        knowledgeBaseRepository.save(knowledgeBase("kb-other", "discovery-profile"));
    }

    @AfterEach
    void cleanStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void discoversMixedSourcesWithConflictsEvidenceAndReviewOnlyProjection() {
        DocumentUploadNode document = documentUploadService.upload("kb-discovery",
            new MockMultipartFile("file", "owned.txt", "text/plain", "string variant owned document".getBytes()));
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of(document.getId()),
            List.of(new SchemaDiscoveryRequest.TextSource("pasted", "string variant pasted text")), null,
            SchemaDiscoveryRequest.DiscoveryGuidance.empty());
        MockMultipartFile file = new MockMultipartFile("files", "date.txt", "text/plain", "date variant request file".getBytes());
        long schemaCountBefore = schemaRepository.count();

        SchemaDiscoveryResponse response = discoveryService.discover("kb-discovery", request, List.of(file));

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(response.sourceOutcomes()).hasSize(3);
        assertThat(response.reproducibility().aiProfileId()).isEqualTo("discovery-profile");
        assertThat(response.reproducibility().aiProfileRevision()).isEqualTo(9);
        assertThat(response.candidates()).filteredOn(candidate -> candidate.identity().equals("node:Person"))
            .singleElement().satisfies(candidate -> {
                assertThat(candidate.supportCount()).isEqualTo(3);
                assertThat(candidate.evidence()).hasSize(3);
            });
        assertThat(response.conflicts()).anySatisfy(conflict -> {
            assertThat(conflict.category()).isEqualTo(ConflictCategory.PROPERTY_TYPE);
            assertThat(conflict.alternatives()).containsExactly("DATE", "STRING");
        });
        assertThat(response.schema().toString()).contains("Person").doesNotContain("birthDate");
        assertThat(schemaRepository.count()).isEqualTo(schemaCountBefore);
    }

    @Test
    void rejectsDocumentOwnedByAnotherKnowledgeBaseWithoutLeakingExistence() {
        DocumentUploadNode document = documentUploadService.upload("kb-other",
            new MockMultipartFile("file", "other.txt", "text/plain", "other content".getBytes()));
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of(document.getId()), List.of(), null,
            SchemaDiscoveryRequest.DiscoveryGuidance.empty());

        assertThatThrownBy(() -> discoveryService.discover("kb-discovery", request, List.of()))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Document not found in knowledge base: " + document.getId());
    }

    private AiProfileNode profile(String id, int revision) {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(id);
        profile.setName("Discovery test profile");
        profile.setBaseUrl("https://example.invalid/v1");
        profile.setApiKey("write-only-test-secret");
        profile.setChatModel("test-chat");
        profile.setEmbeddingModel("test-embedding");
        profile.setEmbeddingDimensions(3);
        profile.setTimeoutSeconds(5);
        profile.setMaxRetries(0);
        profile.setDefaultProfile(false);
        profile.setRevision(revision);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        return profile;
    }

    private KnowledgeBaseNode knowledgeBase(String id, String profileId) {
        KnowledgeBaseNode node = new KnowledgeBaseNode();
        node.setId(id);
        node.setName(id);
        node.setActiveAiProfileId(profileId);
        node.setCreatedAt(Instant.now());
        return node;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DiscoveryAiConfig {

        @Bean
        @Primary
        CandidateExtractionModelAdapter deterministicCandidateExtractionModelAdapter() {
            return new CandidateExtractionModelAdapter(null, null) {
                @Override
                public <T> T extractValidated(
                    String portablePrompt,
                    io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionAttemptContext context,
                    java.util.function.Function<CandidateExtractionResult, T> validator
                ) {
                    return validator.apply(extract(portablePrompt));
                }

                @Override
                public CandidateExtractionResult extract(String portablePrompt) {
                    String type = portablePrompt.contains("date variant") ? "DATE" : "STRING";
                    return new CandidateExtractionResult(
                        List.of(new CandidateExtractionResult.NodeCandidate("Person", null, 0.9, "OBSERVED")),
                        List.of(new CandidateExtractionResult.NodePropertyCandidate("Person", "birthDate", type, false, 0.8, "OBSERVED")),
                        List.of(new CandidateExtractionResult.NodeKeyCandidate("Person", List.of("personId"), 0.8, "INFERRED")),
                        List.of(), List.of(), List.of()
                    );
                }
            };
        }
    }
}
