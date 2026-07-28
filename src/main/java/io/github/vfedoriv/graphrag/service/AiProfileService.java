package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.dto.CreateAiProfileRequest;
import io.github.vfedoriv.graphrag.dto.UpdateAiProfileRequest;
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
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;

@Service
public class AiProfileService implements ApplicationRunner {

    public static final String DEFAULT_PROFILE_ID = "default";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_RETRIES = 2;

    private final AiProfileRepository aiProfileRepository;
    private final AppProperties appProperties;
    private final org.springframework.beans.factory.ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider;
    private final EmbeddingSpacePolicy embeddingSpacePolicy;

    @Autowired
    public AiProfileService(
        AiProfileRepository aiProfileRepository,
        AppProperties appProperties,
        org.springframework.beans.factory.ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider,
        EmbeddingSpacePolicy embeddingSpacePolicy
    ) {
        this.aiProfileRepository = aiProfileRepository;
        this.appProperties = appProperties;
        this.runtimeModelFactoryProvider = runtimeModelFactoryProvider;
        this.embeddingSpacePolicy = embeddingSpacePolicy;
    }

    public AiProfileService(
        AiProfileRepository aiProfileRepository,
        AppProperties appProperties,
        org.springframework.beans.factory.ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider
    ) {
        this(aiProfileRepository, appProperties, runtimeModelFactoryProvider, null);
    }

    @Override
    @GraphTransactional
    public void run(ApplicationArguments args) {
        seedDefaultProfile();
    }

    @GraphTransactional
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
                profile.setEmbeddingDimensions(appProperties.model().embeddingDimensions());
                profile.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
                profile.setMaxRetries(DEFAULT_MAX_RETRIES);
                profile.setDefaultProfile(true);
                profile.setRevision(1);
                profile.setCreatedAt(now);
                profile.setUpdatedAt(now);
                return aiProfileRepository.save(profile);
            });
    }

    @GraphTransactional(readOnly = true)
    public List<AiProfileResponse> list() {
        return aiProfileRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    @GraphTransactional(readOnly = true)
    public AiProfileNode getNode(String id) {
        return aiProfileRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("AI profile not found: " + id));
    }

    @GraphTransactional(readOnly = true)
    public AiProfileResponse get(String id) {
        return toResponse(getNode(id));
    }

    @GraphTransactional
    public AiProfileResponse create(CreateAiProfileRequest request) {
        if (aiProfileRepository.existsById(request.id())) {
            throw new ConflictException("AI profile already exists: " + request.id());
        }
        validateProfile(request.baseUrl(), request.chatModel(), request.embeddingModel(), request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()), maxRetries(request.maxRetries()));
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
            request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()),
            maxRetries(request.maxRetries()),
            Boolean.TRUE.equals(request.defaultProfile())
        );
        profile.setRevision(1);
        profile.setUpdatedAt(now);
        AiProfileNode saved = aiProfileRepository.save(profile);
        if (saved.isDefaultProfile()) {
            unsetOtherDefaults(saved.getId());
        }
        invalidate(saved.getId());
        return toResponse(saved);
    }

    @GraphTransactional
    public AiProfileResponse update(String id, UpdateAiProfileRequest request) {
        AiProfileNode profile = getNode(id);
        validateProfile(request.baseUrl(), request.chatModel(), request.embeddingModel(), request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()), maxRetries(request.maxRetries()));
        EmbeddingSpace requestedEmbeddingSpace = EmbeddingSpaceIdentity.derive(
            request.baseUrl(), request.embeddingModel(), request.embeddingDimensions()
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
            request.embeddingDimensions(),
            timeoutSeconds(request.timeoutSeconds()),
            maxRetries(request.maxRetries()),
            Boolean.TRUE.equals(request.defaultProfile())
        );
        profile.setRevision(profile.getRevision() + 1);
        profile.setUpdatedAt(Instant.now());
        AiProfileNode saved = aiProfileRepository.save(profile);
        if (saved.isDefaultProfile()) {
            unsetOtherDefaults(saved.getId());
        }
        invalidate(saved.getId());
        return toResponse(saved);
    }

    @GraphTransactional
    public void delete(String id) {
        AiProfileNode profile = getNode(id);
        if (profile.isDefaultProfile()) {
            throw new ConflictException("Default AI profile cannot be deleted");
        }
        if (Boolean.TRUE.equals(aiProfileRepository.existsKnowledgeBaseAssignment(id))) {
            throw new ConflictException("AI profile is assigned to at least one knowledge base: " + id);
        }
        aiProfileRepository.deleteById(id);
    }

    @GraphTransactional
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
        int maxRetries
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

    private void rejectIncompatibleProfileUpdate(String profileId, EmbeddingSpace requestedEmbeddingSpace) {
        if (embeddingSpacePolicy == null) {
            return;
        }
        List<String> assignedKnowledgeBaseIds = aiProfileRepository.findAssignedKnowledgeBaseIds(profileId);
        if (assignedKnowledgeBaseIds == null || assignedKnowledgeBaseIds.isEmpty()) {
            return;
        }
        List<String> incompatibleKnowledgeBaseIds = embeddingSpacePolicy.incompatibleKnowledgeBaseIds(
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
        return new AiProfileResponse(
            profile.getId(),
            profile.getName(),
            profile.getBaseUrl(),
            profile.getChatModel(),
            profile.getEmbeddingModel(),
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

    private String mask(String value) {
        if (value.length() <= 8) {
            return "********";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
