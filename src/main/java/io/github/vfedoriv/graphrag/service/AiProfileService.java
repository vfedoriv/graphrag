package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.dto.CreateAiProfileRequest;
import io.github.vfedoriv.graphrag.dto.UpdateAiProfileRequest;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTokenizer;
import io.github.vfedoriv.graphrag.ai.ports.ProfileAssignments;
import java.util.Objects;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.error.EmbeddingSpaceConflictException;
import io.github.vfedoriv.graphrag.repository.AiProfileRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class AiProfileService implements ApplicationRunner {

    public static final String DEFAULT_PROFILE_ID = "default";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_RETRIES = 2;

    private final AiProfileRepository aiProfileRepository;
    private final AppProperties appProperties;
    private final org.springframework.beans.factory.ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider;
    private final EmbeddingCompatibility compatibility;
    private final ProfileAssignments assignments;

    @Autowired
    public AiProfileService(
        AiProfileRepository aiProfileRepository,
        AppProperties appProperties,
        org.springframework.beans.factory.ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider,
        EmbeddingCompatibility compatibility,
        ProfileAssignments assignments
    ) {
        this.aiProfileRepository = aiProfileRepository;
        this.appProperties = appProperties;
        this.runtimeModelFactoryProvider = runtimeModelFactoryProvider;
        this.compatibility = Objects.requireNonNull(compatibility);
        this.assignments = Objects.requireNonNull(assignments);
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDefaultProfile();
    }

    public AiProfileNode seedDefaultProfile() {
        return aiProfileRepository.findFirstByDefaultProfileTrue()
            .orElseGet(() -> {
                Instant now = Instant.now();
                AiProfileNode profile = new AiProfileNode();
                profile.setId(DEFAULT_PROFILE_ID);
                profile.setName("Default OpenAI-compatible profile");
                profile.setBaseUrl(appProperties.model().baseUrl());
                profile.setApiKey(appProperties.model().apiKey());
                profile.setChatModel(appProperties.model().chatModel());
                profile.setEmbeddingModel(appProperties.model().embeddingModel());
                profile.setTokenizerId(null);
                profile.setEmbeddingDimensions(appProperties.model().embeddingDimensions());
                profile.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
                profile.setMaxRetries(DEFAULT_MAX_RETRIES);
                profile.setDefaultProfile(true);
                profile.setRevision(1);
                profile.setCreatedAt(now);
                profile.setUpdatedAt(now);
                try {
                    return aiProfileRepository.save(profile);
                } catch (DataIntegrityViolationException exception) {
                    return aiProfileRepository.findFirstByDefaultProfileTrue()
                        .orElseThrow(() -> exception);
                }
            });
    }

    @RelationalTransactional(readOnly = true)
    public List<AiProfileResponse> list() {
        return aiProfileRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    @RelationalTransactional(readOnly = true)
    public AiProfileNode getNode(String id) {
        return aiProfileRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("AI profile not found: " + id));
    }

    @RelationalTransactional(readOnly = true)
    public AiProfileResponse get(String id) {
        return toResponse(getNode(id));
    }

    @RelationalTransactional
    public AiProfileResponse create(CreateAiProfileRequest request) {
        if (aiProfileRepository.existsById(request.id())) {
            throw new ConflictException("AI profile already exists: " + request.id());
        }
        validateProfile(request.baseUrl(), request.chatModel(), request.embeddingModel(), request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()), maxRetries(request.maxRetries()), request.tokenizerId());
        Instant now = Instant.now();
        AiProfileNode profile = new AiProfileNode();
        profile.setId(request.id());
        profile.setCreatedAt(now);
        applyValues(
            profile,
            request.name(),
            request.baseUrl(),
            request.apiKey(),
            false,
            request.chatModel(),
            request.embeddingModel(),
            validateExplicitTokenizer(request.tokenizerId()),
            request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()),
            maxRetries(request.maxRetries()),
            Boolean.TRUE.equals(request.defaultProfile())
        );
        profile.setRevision(1);
        profile.setUpdatedAt(now);
        if (profile.isDefaultProfile()) {
            unsetOtherDefaults(profile.getId());
        }
        AiProfileNode saved;
        try {
            saved = aiProfileRepository.save(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("AI profile identity or default selection conflicts with existing state");
        }
        invalidate(saved.getId());
        return toResponse(saved);
    }

    @RelationalTransactional
    public AiProfileResponse update(String id, UpdateAiProfileRequest request) {
        AiProfileNode profile = getNode(id);
        validateProfile(request.baseUrl(), request.chatModel(), request.embeddingModel(), request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()), maxRetries(request.maxRetries()), request.tokenizerId());
        TokenizerId requestedTokenizerId = validateExplicitTokenizer(request.tokenizerId());
        EmbeddingTarget requestedEmbeddingSpace = EmbeddingTarget.derive(
            request.baseUrl(), request.embeddingModel(), request.embeddingDimensions(), requestedTokenizerId == null ? null : requestedTokenizerId.value()
        );
        rejectIncompatibleProfileUpdate(profile.getId(), requestedEmbeddingSpace);
        applyValues(
            profile,
            request.name(),
            request.baseUrl(),
            request.apiKey(),
            Boolean.TRUE.equals(request.clearApiKey()),
            request.chatModel(),
            request.embeddingModel(),
            requestedTokenizerId,
            request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()),
            maxRetries(request.maxRetries()),
            Boolean.TRUE.equals(request.defaultProfile())
        );
        profile.setRevision(profile.getRevision() + 1);
        profile.setUpdatedAt(Instant.now());
        if (profile.isDefaultProfile()) {
            unsetOtherDefaults(profile.getId());
        }
        AiProfileNode saved;
        try {
            saved = aiProfileRepository.save(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("AI profile default selection conflicts with existing state");
        }
        invalidate(saved.getId());
        return toResponse(saved);
    }

    @RelationalTransactional
    public void delete(String id) {
        AiProfileNode profile = getNode(id);
        if (profile.isDefaultProfile()) {
            throw new ConflictException("Default AI profile cannot be deleted");
        }
        if (assignments.exists(id)) {
            throw new ConflictException("AI profile is assigned to at least one knowledge base: " + id);
        }
        aiProfileRepository.deleteById(id);
    }

    public AiProfileNode defaultProfile() {
        return aiProfileRepository.findFirstByDefaultProfileTrue()
            .orElseGet(this::seedDefaultProfile);
    }

    private void applyValues(
        AiProfileNode profile,
        String name,
        String baseUrl,
        String apiKey,
        boolean clearApiKey,
        String chatModel,
        String embeddingModel,
        TokenizerId tokenizerId,
        int embeddingDimensions,
        int timeoutSeconds,
        int maxRetries,
        boolean defaultProfile
    ) {
        profile.setName(name.strip());
        profile.setBaseUrl(baseUrl.strip());
        if (clearApiKey) {
            profile.setApiKey(null);
        } else if (apiKey != null) {
            profile.setApiKey(apiKey);
        }
        profile.setChatModel(chatModel.strip());
        profile.setEmbeddingModel(embeddingModel.strip());
        profile.setTokenizerId(tokenizerId);
        profile.setEmbeddingDimensions(embeddingDimensions);
        profile.setTimeoutSeconds(timeoutSeconds);
        profile.setMaxRetries(maxRetries);
        profile.setDefaultProfile(defaultProfile);
    }

    private void validateProfile(
        String baseUrl,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        int timeoutSeconds,
        int maxRetries,
        String tokenizerId
    ) {
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("baseUrl must be an absolute URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("baseUrl must be a valid absolute URL", ex);
        }
        if (chatModel == null || chatModel.isBlank()) {
            throw new IllegalArgumentException("chatModel is required");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel is required");
        }
        if (embeddingDimensions < 1) {
            throw new IllegalArgumentException("embeddingDimensions must be greater than zero");
        }
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be greater than zero");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be greater than or equal to zero");
        }
        validateExplicitTokenizer(tokenizerId);
    }

    private void unsetOtherDefaults(String profileId) {
        aiProfileRepository.unsetDefaultProfileForOthers(profileId);
    }

    private int timeoutSeconds(Integer value) {
        return value == null ? DEFAULT_TIMEOUT_SECONDS : value;
    }

    private int maxRetries(Integer value) {
        return value == null ? DEFAULT_MAX_RETRIES : value;
    }

    private void invalidate(String profileId) {
        AiRuntimeModelFactory factory = runtimeModelFactoryProvider.getIfAvailable();
        if (factory != null) {
            factory.invalidate(profileId);
        }
    }

    private void rejectIncompatibleProfileUpdate(String profileId, EmbeddingTarget requestedEmbeddingSpace) {
        List<String> assignedKnowledgeBaseIds = assignments.knowledgeBaseIds(profileId);
        if (assignedKnowledgeBaseIds == null || assignedKnowledgeBaseIds.isEmpty()) {
            return;
        }
        List<String> incompatibleKnowledgeBaseIds = compatibility.incompatibleKnowledgeBaseIds(
            assignedKnowledgeBaseIds,
            requestedEmbeddingSpace
        );
        if (!incompatibleKnowledgeBaseIds.isEmpty()) {
            throw new EmbeddingSpaceConflictException(
                "AI profile update would make stored embeddings incompatible with assigned knowledge bases",
                incompatibleKnowledgeBaseIds
            );
        }
    }

    public AiProfileResponse toResponse(AiProfileNode profile) {
        boolean configured = profile.getApiKey() != null && !profile.getApiKey().isBlank();
        String resolvedTokenizer = EmbeddingTokenizer.resolve(
            profile.getTokenizerId() == null ? null : profile.getTokenizerId().value(), profile.getEmbeddingModel());
        return new AiProfileResponse(
            profile.getId(),
            profile.getName(),
            profile.getBaseUrl(),
            profile.getChatModel(),
            profile.getEmbeddingModel(),
            profile.getTokenizerId() == null ? null : profile.getTokenizerId().value(),
            resolvedTokenizer,
            profile.getEmbeddingDimensions(),
            profile.getTimeoutSeconds(),
            profile.getMaxRetries(),
            profile.isDefaultProfile(),
            profile.getRevision(),
            configured,
            configured ? mask(profile.getApiKey()) : null,
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }

    private TokenizerId validateExplicitTokenizer(String value) {
        return value == null ? null : TokenizerId.explicit(value);
    }

    private String mask(String value) {
        if (value.length() <= 8) {
            return "********";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
