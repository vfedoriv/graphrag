package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import io.github.vfedoriv.graphrag.dto.SchemaGenerationResult;
import io.github.vfedoriv.graphrag.dto.SchemaGenerationWarning;
import io.github.vfedoriv.graphrag.application.schema.SchemaGenerationPromptFactory;
import io.github.vfedoriv.graphrag.application.schema.SchemaGenerationWarningFactory;
import io.github.vfedoriv.graphrag.application.schema.SchemaGraphMapper;
import io.github.vfedoriv.graphrag.graph.LLMGraphTransformerExt;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.infrastructure.ai.SchemaGenerationModelAdapter;
import io.github.vfedoriv.graphrag.llm.SpringAiLangChain4jChatModelAdapter;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.schema.NodeKeySupport;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LangChain4jSchemaGenerationService implements SchemaGenerationService {

    private final ObjectMapper objectMapper;
    private final AiObservationService aiObservationService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SchemaGenerationPromptFactory promptFactory;
    private final SchemaGenerationModelAdapter modelAdapter;
    private final SchemaGraphMapper graphMapper;
    private final SchemaGenerationWarningFactory warningFactory;

    @Autowired
    public LangChain4jSchemaGenerationService(
        ObjectMapper objectMapper,
        ProfileScopedAiClientResolver aiClientResolver,
        AiObservationService aiObservationService,
        KnowledgeBaseService knowledgeBaseService
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
        ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider,
        KnowledgeBaseService knowledgeBaseService
    ) {
        this(
            new ObjectMapper(),
            new ProfileScopedAiClientResolver(
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
