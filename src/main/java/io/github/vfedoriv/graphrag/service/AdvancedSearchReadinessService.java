package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchReadinessDtos.ReadinessIssue;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchReadinessDtos.ReadinessResponse;
import io.github.vfedoriv.graphrag.error.AdvancedSearchReadinessConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchReadinessService {
    public static final String PROFILE_UNAVAILABLE = "PROFILE_UNAVAILABLE";
    public static final String CHAT_CONFIGURATION_UNAVAILABLE = "CHAT_CONFIGURATION_UNAVAILABLE";
    public static final String EMBEDDING_CONFIGURATION_UNAVAILABLE = "EMBEDDING_CONFIGURATION_UNAVAILABLE";
    public static final String EMBEDDING_SPACE_INCOMPATIBLE = "EMBEDDING_SPACE_INCOMPATIBLE";
    public static final String PROFILE_CHANGED = "PROFILE_CHANGED";
    public static final String SCHEMA_UNAVAILABLE = "SCHEMA_UNAVAILABLE";
    public static final String EMPTY_CORPUS = "EMPTY_CORPUS";

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiProfileService aiProfileService;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final EmbeddingSpacePolicy embeddingSpacePolicy;
    private final AiRuntimeModelFactory runtimeModelFactory;

    public AdvancedSearchReadinessService(
        KnowledgeBaseRepository knowledgeBaseRepository,
        AiProfileService aiProfileService,
        SchemaDefinitionRepository schemaDefinitionRepository,
        EmbeddingSpacePolicy embeddingSpacePolicy,
        AiRuntimeModelFactory runtimeModelFactory
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.aiProfileService = aiProfileService;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.embeddingSpacePolicy = embeddingSpacePolicy;
        this.runtimeModelFactory = runtimeModelFactory;
    }

    public ReadinessResponse evaluate(String knowledgeBaseId) {
        KnowledgeBaseNode knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        List<ReadinessIssue> blockers = new ArrayList<>();
        List<ReadinessIssue> informational = new ArrayList<>();
        AiProfileNode profile = resolveProfile(knowledgeBase, blockers);

        boolean graphAvailable = knowledgeBase.getActiveSchemaId() != null
            && !knowledgeBase.getActiveSchemaId().isBlank()
            && schemaDefinitionRepository.findById(knowledgeBase.getActiveSchemaId()).isPresent();
        if (!graphAvailable) {
            informational.add(issue(SCHEMA_UNAVAILABLE, "No active schema is available; text retrieval remains available."));
        }

        boolean embedded = embeddingSpacePolicy.hasEmbeddedChunks(knowledgeBaseId);
        if (!embedded) {
            informational.add(issue(EMPTY_CORPUS, "No embedded chunks are available; the run may complete with insufficient evidence."));
        }

        if (profile != null) {
            evaluateChatConfiguration(profile, blockers);
            if (embedded) {
                evaluateEmbeddingConfiguration(knowledgeBaseId, profile, blockers);
            }
        }
        return new ReadinessResponse(
            knowledgeBaseId,
            blockers.isEmpty(),
            profile == null ? null : profile.getId(),
            profile == null ? 0 : profile.getRevision(),
            graphAvailable,
            embedded,
            blockers,
            informational
        );
    }

    public void requireReady(ReadinessResponse readiness) {
        if (readiness == null || !readiness.ready()) {
            throw new AdvancedSearchReadinessConflictException(
                readiness == null ? List.of(issue(PROFILE_UNAVAILABLE, "AI profile readiness could not be evaluated."))
                    : readiness.blockers());
        }
    }

    public void requireSameProfile(ReadinessResponse expected, ReadinessResponse actual) {
        if (expected == null || actual == null || !java.util.Objects.equals(expected.profileId(), actual.profileId())
            || expected.profileRevision() != actual.profileRevision()) {
            throw new AdvancedSearchReadinessConflictException(
                List.of(issue(PROFILE_CHANGED, "The active AI profile changed while the run was being admitted.")));
        }
    }

    private AiProfileNode resolveProfile(KnowledgeBaseNode knowledgeBase, List<ReadinessIssue> blockers) {
        try {
            String profileId = knowledgeBase.getActiveAiProfileId();
            return profileId == null || profileId.isBlank()
                ? aiProfileService.defaultProfile() : aiProfileService.getNode(profileId);
        } catch (NotFoundException exception) {
            blockers.add(issue(PROFILE_UNAVAILABLE, "The active AI profile is unavailable."));
            return null;
        }
    }

    private void evaluateChatConfiguration(AiProfileNode profile, List<ReadinessIssue> blockers) {
        if (!hasValidBaseUrl(profile.getBaseUrl()) || !hasText(profile.getChatModel())) {
            blockers.add(issue(CHAT_CONFIGURATION_UNAVAILABLE, "The active chat-provider configuration is incomplete."));
            return;
        }
        try {
            runtimeModelFactory.chatModel(profile.getId());
        } catch (RuntimeException exception) {
            blockers.add(issue(CHAT_CONFIGURATION_UNAVAILABLE, "The active chat-provider configuration cannot be constructed."));
        }
    }

    private void evaluateEmbeddingConfiguration(
        String knowledgeBaseId,
        AiProfileNode profile,
        List<ReadinessIssue> blockers
    ) {
        if (!hasValidBaseUrl(profile.getBaseUrl()) || !hasText(profile.getEmbeddingModel())
            || profile.getEmbeddingDimensions() < 1) {
            blockers.add(issue(EMBEDDING_CONFIGURATION_UNAVAILABLE, "The active embedding configuration is incomplete."));
            return;
        }
        try {
            runtimeModelFactory.embeddingModel(profile.getId());
        } catch (RuntimeException exception) {
            blockers.add(issue(EMBEDDING_CONFIGURATION_UNAVAILABLE, "The active embedding client cannot be constructed."));
            return;
        }
        try {
            embeddingSpacePolicy.requireCompatible(knowledgeBaseId, profile);
        } catch (RuntimeException exception) {
            blockers.add(issue(EMBEDDING_SPACE_INCOMPATIBLE, "Stored embeddings are incompatible with the active profile."));
        }
    }

    private boolean hasValidBaseUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.strip());
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ReadinessIssue issue(String code, String description) {
        return new ReadinessIssue(code, description);
    }
}
