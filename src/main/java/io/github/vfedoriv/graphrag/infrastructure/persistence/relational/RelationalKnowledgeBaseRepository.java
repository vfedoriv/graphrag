package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.KnowledgeBaseEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseSchemaRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalKnowledgeBaseRepository implements KnowledgeBaseRepository {
    private final JpaKnowledgeBaseRepository repository;
    private final JpaKnowledgeBaseSchemaRepository associationRepository;

    public RelationalKnowledgeBaseRepository(
        JpaKnowledgeBaseRepository repository,
        JpaKnowledgeBaseSchemaRepository associationRepository
    ) {
        this.repository = repository;
        this.associationRepository = associationRepository;
    }

    @Override
    public List<KnowledgeBaseNode> findAllByOrderByCreatedAtDesc() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<KnowledgeBaseNode> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    public KnowledgeBaseNode save(KnowledgeBaseNode knowledgeBase) {
        Instant now = Instant.now();
        KnowledgeBaseEntity entity = toEntity(knowledgeBase);
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
        repository.flush();
    }

    @Override
    @RelationalTransactional
    public boolean assignAiProfile(String id, long expectedVersion, String profileId) {
        return repository.assignAiProfile(id, expectedVersion, profileId) == 1;
    }

    @Override
    public Boolean existsAiProfileAssignment(String profileId) {
        return repository.existsByActiveAiProfileId(profileId);
    }

    @Override
    public List<String> findIdsByActiveAiProfileId(String profileId) {
        return repository.findAllByActiveAiProfileId(profileId).stream().map(KnowledgeBaseEntity::getId).toList();
    }

    private KnowledgeBaseEntity toEntity(KnowledgeBaseNode source) {
        KnowledgeBaseEntity target = new KnowledgeBaseEntity();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setActiveAiProfileId(source.getActiveAiProfileId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }

    private KnowledgeBaseNode toDomain(KnowledgeBaseEntity source) {
        KnowledgeBaseNode target = new KnowledgeBaseNode();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setActiveAiProfileId(source.getActiveAiProfileId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getVersion());
        associationRepository.findFirstByKnowledgeBaseIdAndActiveTrue(source.getId())
            .ifPresent(association -> target.setActiveSchemaId(association.getSchemaId()));
        return target;
    }
}
