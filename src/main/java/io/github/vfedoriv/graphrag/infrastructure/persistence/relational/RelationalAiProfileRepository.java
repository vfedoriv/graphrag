package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.document.chunking.TokenizerId;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AiProfileEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaAiProfileRepository;
import io.github.vfedoriv.graphrag.repository.AiProfileRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalAiProfileRepository implements AiProfileRepository {

    private final JpaAiProfileRepository repository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public RelationalAiProfileRepository(
        JpaAiProfileRepository repository,
        KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.repository = repository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Override
    public List<AiProfileNode> findAllByOrderByCreatedAtDesc() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AiProfileNode> findFirstByDefaultProfileTrue() {
        return repository.findFirstByDefaultProfileTrue().map(this::toDomain);
    }

    @Override
    public Boolean existsByDefaultProfileTrue() {
        return repository.existsByDefaultProfileTrue();
    }

    @Override
    public Optional<AiProfileNode> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    public AiProfileNode save(AiProfileNode profile) {
        return toDomain(repository.saveAndFlush(toEntity(profile)));
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
        repository.flush();
    }

    @Override
    public Long unsetDefaultProfileForOthers(String profileId) {
        return (long) repository.unsetDefaultProfileForOthers(profileId);
    }

    @Override
    public Boolean existsKnowledgeBaseAssignment(String profileId) {
        return knowledgeBaseRepository.existsAiProfileAssignment(profileId);
    }

    @Override
    public List<String> findAssignedKnowledgeBaseIds(String profileId) {
        return knowledgeBaseRepository.findIdsByActiveAiProfileId(profileId);
    }

    private AiProfileEntity toEntity(AiProfileNode source) {
        AiProfileEntity target = new AiProfileEntity();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setBaseUrl(source.getBaseUrl());
        target.setApiKey(source.getApiKey());
        target.setChatModel(source.getChatModel());
        target.setEmbeddingModel(source.getEmbeddingModel());
        target.setTokenizerId(source.getTokenizerId() == null ? null : source.getTokenizerId().value());
        target.setEmbeddingDimensions(source.getEmbeddingDimensions());
        target.setTimeoutSeconds(source.getTimeoutSeconds());
        target.setMaxRetries(source.getMaxRetries());
        target.setDefaultProfile(source.isDefaultProfile());
        target.setRevision(source.getRevision());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }

    private AiProfileNode toDomain(AiProfileEntity source) {
        AiProfileNode target = new AiProfileNode();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setBaseUrl(source.getBaseUrl());
        target.setApiKey(source.getApiKey());
        target.setChatModel(source.getChatModel());
        target.setEmbeddingModel(source.getEmbeddingModel());
        target.setTokenizerId(source.getTokenizerId() == null ? null : new TokenizerId(source.getTokenizerId()));
        target.setEmbeddingDimensions(source.getEmbeddingDimensions());
        target.setTimeoutSeconds(source.getTimeoutSeconds());
        target.setMaxRetries(source.getMaxRetries());
        target.setDefaultProfile(source.isDefaultProfile());
        target.setRevision(source.getRevision());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }
}
