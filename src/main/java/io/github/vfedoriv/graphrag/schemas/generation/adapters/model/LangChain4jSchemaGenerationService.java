package io.github.vfedoriv.graphrag.schemas.generation.adapters.model;

import io.github.vfedoriv.graphrag.schemas.generation.application.SchemaGenerationService;

import io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseProfiles;

import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;

import io.github.vfedoriv.graphrag.ai.models.AiModelAccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGenerationResult;
import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGenerationWarning;
import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGenerationPromptFactory;
import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGenerationWarningFactory;
import io.github.vfedoriv.graphrag.schemas.generation.domain.SchemaGraphMapper;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.schemas.generation.adapters.model.SchemaGenerationModelAdapter;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LangChain4jSchemaGenerationService implements SchemaGenerationService {

    private final ObjectMapper objectMapper;
    private final AiObservationService aiObservationService;
    private final KnowledgeBaseProfiles knowledgeBaseService;
    private final SchemaGenerationPromptFactory promptFactory;
    private final SchemaGenerationModelAdapter modelAdapter;
    private final SchemaGraphMapper graphMapper;
    private final SchemaGenerationWarningFactory warningFactory;

    @Autowired
    public LangChain4jSchemaGenerationService(
        ObjectMapper objectMapper,
        ProfileScopedAiClientResolver aiClientResolver,
        AiObservationService aiObservationService,
        KnowledgeBaseProfiles knowledgeBaseService
    ) {
        this.objectMapper = objectMapper;
        this.aiObservationService = aiObservationService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.promptFactory = new SchemaGenerationPromptFactory();
        this.modelAdapter = new SchemaGenerationModelAdapter(aiClientResolver, aiObservationService);
        this.graphMapper = new SchemaGraphMapper();
        this.warningFactory = new SchemaGenerationWarningFactory();
    }

    public LangChain4jSchemaGenerationService(
        ObjectProvider<org.springframework.ai.chat.model.ChatModel> springChatModelProvider,
        AiObservationService aiObservationService,
        ObjectProvider<? extends AiModelAccess> runtimeModelFactoryProvider,
        KnowledgeBaseProfiles knowledgeBaseService
    ) {
        this(
            new ObjectMapper(),
            ProfileScopedAiClientResolver.fromProviders(
                new EmptyObjectProvider<>(),
                springChatModelProvider,
                runtimeModelFactoryProvider
            ),
            aiObservationService,
            knowledgeBaseService
        );
    }

    @Override
    public SchemaGenerationResult generate(String name, int version, String description, String text, String example) {
        long startNanos = System.nanoTime();
        log.info(
            "Schema JSON generation started: name={}, version={}, descriptionPresent={}, textLength={}, exampleLength={}, textFingerprint={}",
            name,
            version,
            description != null && !description.isBlank(),
            LogMetadata.length(text),
            LogMetadata.length(example),
            LogMetadata.fingerprint(text)
        );
        try (AiObservationScope workflow = aiObservationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_SCHEMA_GENERATION,
            name,
            Map.of(
                "ai.schema.version", String.valueOf(version),
                "ai.schema.description_present", String.valueOf(description != null && !description.isBlank()),
                "ai.text.length", String.valueOf(LogMetadata.length(text)),
                "ai.example.length", String.valueOf(LogMetadata.length(example))
            )
        ))) {
            try {
                GraphDocument graphDocument = modelAdapter.extractGraph(text, promptFactory.schemaContract(), example);
                SchemaDocument schema = inferSchema(name, version, description, graphDocument);
                List<SchemaGenerationWarning> warnings = buildKeyPropertyWarnings(schema);
                String json = objectMapper.writeValueAsString(schema);
                log.info(
                    "Schema JSON generation completed: name={}, version={}, nodes={}, relationships={}, warningCount={}, jsonLength={}, elapsedMs={}",
                    name,
                    version,
                    schema.nodes().size(),
                    schema.relationships().size(),
                    warnings.size(),
                    json.length(),
                    LogMetadata.elapsedMillis(startNanos)
                );
                workflow.highCardinalityAttribute("ai.schema.node_count", String.valueOf(schema.nodes().size()));
                workflow.highCardinalityAttribute("ai.schema.relationship_count", String.valueOf(schema.relationships().size()));
                workflow.success();
                return new SchemaGenerationResult(json, warnings);
            } catch (JsonProcessingException e) {
                workflow.error(e);
                log.error("Failed to serialize generated schema to JSON: name={}, version={}, exceptionType={}", name, version, LogMetadata.exceptionType(e));
                throw new IllegalStateException("Failed to serialize generated schema to JSON", e);
            } catch (RuntimeException e) {
                workflow.error(e);
                throw e;
            }
        }
    }

    @Override
    public SchemaGenerationResult generate(String knowledgeBaseId, String name, int version, String description, String text, String example) {
        String profileId = knowledgeBaseService.activeAiProfile(knowledgeBaseId).getId();
        return AiProfileContext.withProfile(profileId, () -> generate(name, version, description, text, example));
    }

    @Override
    public String generateExample(String text, String userPrompt) {
        long startNanos = System.nanoTime();
        String prompt = promptFactory.examplePrompt(text, userPrompt);
        log.info(
            "Schema example generation started: textLength={}, userPromptLength={}, promptFingerprint={}",
            LogMetadata.length(text),
            LogMetadata.length(userPrompt),
            LogMetadata.fingerprint(prompt)
        );
        String response = modelAdapter.generateExample(prompt);
        log.info(
            "Schema example generation completed: responseLength={}, responseFingerprint={}, elapsedMs={}",
            LogMetadata.length(response),
            LogMetadata.fingerprint(response),
            LogMetadata.elapsedMillis(startNanos)
        );
        return response;
    }

    @Override
    public String generateExample(String knowledgeBaseId, String text, String userPrompt) {
        String profileId = knowledgeBaseService.activeAiProfile(knowledgeBaseId).getId();
        return AiProfileContext.withProfile(profileId, () -> generateExample(text, userPrompt));
    }

    SchemaDocument inferSchema(String name, int version, String description, GraphDocument graphDocument) {
        return graphMapper.map(name, version, description, graphDocument);
    }

    static List<SchemaGenerationWarning> buildKeyPropertyWarnings(SchemaDocument schema) {
        return new SchemaGenerationWarningFactory().build(schema);
    }

}
