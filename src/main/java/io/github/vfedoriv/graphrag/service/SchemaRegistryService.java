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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
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
    public SchemaDefinitionNode createSchema(String json, SchemaSourceType sourceType) {
        log.info("Creating schema: sourceType={}, contentLength={}", sourceType, json == null ? 0 : json.length());
        SchemaDocument doc = schemaParser.parse(json);
        List<String> errors = schemaValidator.validate(doc);
        if (!errors.isEmpty()) {
            log.info("Schema validation failed before create: name={}, version={}, errorCount={}", doc.name(), doc.version(), errors.size());
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
        node.setFormat(SchemaFormat.JSON);
        node.setContent(json);
        node.setContentHash(sha256(json));
        node.setStatus(SchemaStatus.INACTIVE);
        node.setCreatedAt(Instant.now());
        SchemaDefinitionNode saved = schemaRepository.save(node);
        log.info("Schema created: schemaId={}, name={}, version={}, sourceType={}", saved.getId(), saved.getName(), saved.getVersion(), saved.getSourceType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SchemaDefinitionNode> listSchemas() {
        log.info("Listing schemas");
        List<SchemaDefinitionNode> schemas = schemaRepository.findAll();
        log.info("Schemas listed: count={}", schemas.size());
        return schemas;
    }

    @Transactional(readOnly = true)
    public SchemaDefinitionNode getSchema(String schemaId) {
        log.info("Loading schema: schemaId={}", schemaId);
        SchemaDefinitionNode schema = schemaRepository.findById(schemaId).orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
        log.info("Schema loaded: schemaId={}, name={}, version={}", schema.getId(), schema.getName(), schema.getVersion());
        return schema;
    }

    @Transactional(readOnly = true)
    public List<String> validateJson(String json) {
        log.info("Validating schema JSON: contentLength={}", json == null ? 0 : json.length());
        SchemaDocument doc = schemaParser.parse(json);
        List<String> errors = schemaValidator.validate(doc);
        log.info("Schema JSON validation completed: name={}, version={}, valid={}, errorCount={}", doc.name(), doc.version(), errors.isEmpty(), errors.size());
        return errors;
    }

    @Transactional
    public void activateSchema(String knowledgeBaseId, String schemaId) {
        log.info("Activating schema: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
        SchemaDefinitionNode schema = getSchema(schemaId);
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseGet(() -> {
                KnowledgeBaseNode created = new KnowledgeBaseNode();
                created.setId(knowledgeBaseId);
                created.setName("kb-" + knowledgeBaseId);
                created.setCreatedAt(Instant.now());
                return knowledgeBaseRepository.save(created);
            });
        if (schemaId.equals(kb.getActiveSchemaId())) {
            if (schema.getStatus() != SchemaStatus.ACTIVE) {
                schema.setStatus(SchemaStatus.ACTIVE);
                schemaRepository.save(schema);
            }
            log.info("Schema already active: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
            return;
        }
        kb.setActiveSchemaId(schema.getId());
        knowledgeBaseRepository.save(kb);

        neo4jClient.query("""
            MATCH (kb:KnowledgeBase {id: $knowledgeBaseId})
            MATCH (s:SchemaDefinition {id: $schemaId})
            MERGE (kb)-[:USES_SCHEMA]->(s)
            """)
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(schemaId).to("schemaId")
            .run();

        neo4jClient.query("""
            MATCH (kb:KnowledgeBase {id: $knowledgeBaseId})-[:USES_SCHEMA]->(s:SchemaDefinition)
            SET s.status = CASE WHEN s.id = $schemaId THEN 'ACTIVE' ELSE 'INACTIVE' END
            """)
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(schemaId).to("schemaId")
            .run();
        log.info("Schema activated: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 digest algorithm is unavailable", e);
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
