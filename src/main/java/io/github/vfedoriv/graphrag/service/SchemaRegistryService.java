package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
@Slf4j
public class SchemaRegistryService {

    private final SchemaParser schemaParser;
    private final SchemaValidator schemaValidator;
    private final SchemaDefinitionRepository schemaRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final Neo4jClient neo4jClient;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;

    @Autowired
    public SchemaRegistryService(
        SchemaParser schemaParser,
        SchemaValidator schemaValidator,
        SchemaDefinitionRepository schemaRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        Neo4jClient neo4jClient,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService
    ) {
        this.schemaParser = schemaParser;
        this.schemaValidator = schemaValidator;
        this.schemaRepository = schemaRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.neo4jClient = neo4jClient;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
    }

    public SchemaRegistryService(
        SchemaParser schemaParser,
        SchemaValidator schemaValidator,
        SchemaDefinitionRepository schemaRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        Neo4jClient neo4jClient
    ) {
        this(schemaParser, schemaValidator, schemaRepository, knowledgeBaseRepository, neo4jClient, null);
    }

    @GraphTransactional
    public SchemaDefinitionNode createSchema(String json, SchemaSourceType sourceType) {
        return createSchema(json, sourceType, null);
    }

    @GraphTransactional
    public SchemaDefinitionNode createSchema(String json, SchemaSourceType sourceType, String knowledgeBaseId) {
        log.info(
            "Creating schema: sourceType={}, knowledgeBaseId={}, contentLength={}",
            sourceType,
            knowledgeBaseId,
            json == null ? 0 : json.length()
        );
        if (knowledgeBaseId != null) {
            rejectBlankKnowledgeBaseId(knowledgeBaseId);
            requireKnowledgeBase(knowledgeBaseId);
        }
        SchemaDocument doc = schemaParser.parse(json);
        List<String> errors = schemaValidator.validate(doc);
        if (!errors.isEmpty()) {
            log.info("Schema validation failed before create: name={}, version={}, errorCount={}", doc.name(), doc.version(), errors.size());
            throw new SchemaValidationException(errors);
        }

        if (Boolean.TRUE.equals(schemaRepository.existsByNameAndVersion(doc.name(), doc.version()))) {
            throw new ConflictException(
                "Schema version is immutable and already exists for name=" + doc.name() + ", version=" + doc.version()
            );
        }

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
        if (knowledgeBaseId != null) {
            schemaRepository.associateWithKnowledgeBase(knowledgeBaseId, saved.getId());
        }
        log.info(
            "Schema created: schemaId={}, name={}, version={}, sourceType={}, knowledgeBaseId={}",
            saved.getId(),
            saved.getName(),
            saved.getVersion(),
            saved.getSourceType(),
            knowledgeBaseId
        );
        return saved;
    }

    @GraphTransactional
    public SchemaDefinitionNode createGeneratedInactiveSchema(String json, String knowledgeBaseId) {
        return createSchema(json, SchemaSourceType.GENERATED, knowledgeBaseId);
    }

    @GraphTransactional(readOnly = true)
    public List<SchemaDefinitionNode> listSchemas() {
        log.info("Listing schemas");
        List<SchemaDefinitionNode> schemas = schemaRepository.findAll();
        log.info("Schemas listed: count={}", schemas.size());
        return schemas;
    }

    @GraphTransactional(readOnly = true)
    public List<SchemaDefinitionNode> listSchemasByKnowledgeBase(String knowledgeBaseId) {
        log.info("Listing schemas by knowledge base: knowledgeBaseId={}", knowledgeBaseId);
        if (!knowledgeBaseRepository.existsById(knowledgeBaseId)) {
            throw new NotFoundException("Knowledge base not found: " + knowledgeBaseId);
        }
        List<SchemaDefinitionNode> schemas = schemaRepository.findAllByKnowledgeBaseId(knowledgeBaseId);
        log.info("Schemas listed by knowledge base: knowledgeBaseId={}, count={}", knowledgeBaseId, schemas.size());
        return schemas;
    }

    @GraphTransactional(readOnly = true)
    public SchemaDefinitionNode getSchema(String schemaId) {
        log.info("Loading schema: schemaId={}", schemaId);
        SchemaDefinitionNode schema = schemaRepository.findById(schemaId).orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
        log.info("Schema loaded: schemaId={}, name={}, version={}", schema.getId(), schema.getName(), schema.getVersion());
        return schema;
    }

    @GraphTransactional
    public SchemaDefinitionNode updateSchema(String schemaId, String json, SchemaSourceType sourceType) {
        log.info("Updating schema: schemaId={}, sourceType={}, contentLength={}", schemaId, sourceType, json == null ? 0 : json.length());
        SchemaDefinitionNode schema = schemaRepository.findById(schemaId)
            .orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
        rejectIfActive(schemaId, "Cannot update active schema: ");

        SchemaDocument doc = schemaParser.parse(json);
        List<String> errors = schemaValidator.validate(doc);
        if (!errors.isEmpty()) {
            log.info("Schema validation failed before update: schemaId={}, name={}, version={}, errorCount={}", schemaId, doc.name(), doc.version(), errors.size());
            throw new SchemaValidationException(errors);
        }
        if (!schema.getName().equals(doc.name()) || schema.getVersion() != doc.version()) {
            throw new ConflictException(
                "Schema identity is immutable for schemaId=" + schemaId
                    + "; expected name=" + schema.getName() + ", version=" + schema.getVersion()
            );
        }

        schema.setSourceType(sourceType == null ? schema.getSourceType() : sourceType);
        schema.setFormat(SchemaFormat.JSON);
        schema.setContent(json);
        schema.setContentHash(sha256(json));
        SchemaDefinitionNode saved = schemaRepository.save(schema);
        log.info("Schema updated: schemaId={}, name={}, version={}", saved.getId(), saved.getName(), saved.getVersion());
        return saved;
    }

    @GraphTransactional(readOnly = true)
    public List<String> validateJson(String json) {
        log.info("Validating schema JSON: contentLength={}", json == null ? 0 : json.length());
        SchemaDocument doc = schemaParser.parse(json);
        List<String> errors = schemaValidator.validate(doc);
        log.info("Schema JSON validation completed: name={}, version={}, valid={}, errorCount={}", doc.name(), doc.version(), errors.isEmpty(), errors.size());
        return errors;
    }

    @GraphTransactional
    public void activateSchema(String knowledgeBaseId, String schemaId) {
        log.info("Activating schema: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
        SchemaDefinitionNode schema = getSchema(schemaId);
        KnowledgeBaseNode kb = knowledgeBaseLifecycleService == null
            ? knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseGet(() -> {
                    KnowledgeBaseNode created = new KnowledgeBaseNode();
                    created.setId(knowledgeBaseId);
                    created.setName("kb-" + knowledgeBaseId);
                    created.setCreatedAt(Instant.now());
                    return knowledgeBaseRepository.save(created);
                })
            : knowledgeBaseLifecycleService.provision(knowledgeBaseId, "kb-" + knowledgeBaseId);
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

    @GraphTransactional
    public void attachSchema(String knowledgeBaseId, String schemaId) {
        log.info("Attaching schema: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
        rejectBlankKnowledgeBaseId(knowledgeBaseId);
        getSchema(schemaId);
        requireKnowledgeBase(knowledgeBaseId);
        schemaRepository.associateWithKnowledgeBase(knowledgeBaseId, schemaId);
        log.info("Schema attached: knowledgeBaseId={}, schemaId={}", knowledgeBaseId, schemaId);
    }

    @GraphTransactional
    public void deleteSchema(String schemaId) {
        log.info("Deleting schema: schemaId={}", schemaId);
        SchemaDefinitionNode schema = schemaRepository.findById(schemaId)
            .orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
        rejectIfActive(schemaId, "Cannot delete active schema: ");
        schemaRepository.detachKnowledgeBaseAssociations(schemaId);
        schemaRepository.delete(schema);
        log.info("Schema deleted: schemaId={}", schemaId);
    }

    private void rejectIfActive(String schemaId, String messagePrefix) {
        if (Boolean.TRUE.equals(schemaRepository.existsActiveKnowledgeBaseReference(schemaId))) {
            throw new ConflictException(messagePrefix + schemaId);
        }
    }

    private KnowledgeBaseNode requireKnowledgeBase(String knowledgeBaseId) {
        return knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
    }

    private void rejectBlankKnowledgeBaseId(String knowledgeBaseId) {
        if (knowledgeBaseId.isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 digest algorithm is unavailable: exceptionType={}", LogMetadata.exceptionType(e));
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
