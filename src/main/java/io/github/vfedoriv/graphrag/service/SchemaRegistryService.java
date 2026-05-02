package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import io.github.vfedoriv.graphrag.schema.SchemaValidationException;
import io.github.vfedoriv.graphrag.schema.SchemaValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaRegistryService {

    private final SchemaParser schemaParser;
    private final SchemaValidator schemaValidator;
    private final SchemaDefinitionRepository schemaRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final Neo4jClient neo4jClient;

    public SchemaRegistryService(
        SchemaParser schemaParser,
        SchemaValidator schemaValidator,
        SchemaDefinitionRepository schemaRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        Neo4jClient neo4jClient
    ) {
        this.schemaParser = schemaParser;
        this.schemaValidator = schemaValidator;
        this.schemaRepository = schemaRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.neo4jClient = neo4jClient;
    }

    @Transactional
    public SchemaDefinitionNode createSchema(String yaml, SchemaSourceType sourceType) {
        SchemaDocument doc = schemaParser.parse(yaml);
        List<String> errors = schemaValidator.validate(doc);
        if (!errors.isEmpty()) {
            throw new SchemaValidationException(errors);
        }

        schemaRepository.findByNameAndVersion(doc.name(), doc.version()).ifPresent(existing -> {
            throw new ConflictException(
                "Schema version is immutable and already exists for name=" + doc.name() + ", version=" + doc.version()
            );
        });

        SchemaDefinitionNode node = new SchemaDefinitionNode();
        node.setId(UUID.randomUUID().toString());
        node.setName(doc.name());
        node.setVersion(doc.version());
        node.setSourceType(sourceType == null ? SchemaSourceType.PREDEFINED : sourceType);
        node.setFormat(SchemaFormat.YAML);
        node.setContent(yaml);
        node.setContentHash(sha256(yaml));
        node.setStatus(SchemaStatus.INACTIVE);
        node.setCreatedAt(Instant.now());
        return schemaRepository.save(node);
    }

    @Transactional(readOnly = true)
    public List<SchemaDefinitionNode> listSchemas() {
        return schemaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public SchemaDefinitionNode getSchema(String schemaId) {
        return schemaRepository.findById(schemaId).orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
    }

    @Transactional(readOnly = true)
    public List<String> validateYaml(String yaml) {
        SchemaDocument doc = schemaParser.parse(yaml);
        return schemaValidator.validate(doc);
    }

    @Transactional
    public void activateSchema(String knowledgeBaseId, String schemaId) {
        SchemaDefinitionNode schema = getSchema(schemaId);
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseGet(() -> {
                KnowledgeBaseNode created = new KnowledgeBaseNode();
                created.setId(knowledgeBaseId);
                created.setName("kb-" + knowledgeBaseId);
                created.setCreatedAt(Instant.now());
                return knowledgeBaseRepository.save(created);
            });
        kb.setActiveSchemaId(schema.getId());
        knowledgeBaseRepository.save(kb);
        schema.setStatus(SchemaStatus.ACTIVE);
        schemaRepository.save(schema);

        neo4jClient.query("""
            MATCH (kb:KnowledgeBase {id: $knowledgeBaseId})
            MATCH (s:SchemaDefinition {id: $schemaId})
            MERGE (kb)-[:USES_SCHEMA]->(s)
            """)
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(schemaId).to("schemaId")
            .run();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
