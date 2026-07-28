package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.RuntimeSettingOverrideEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaRuntimeSettingOverrideRepository;
import io.github.vfedoriv.graphrag.repository.RuntimeSettingOverrideRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalRuntimeSettingOverrideRepository implements RuntimeSettingOverrideRepository {

    private final JpaRuntimeSettingOverrideRepository repository;

    public RelationalRuntimeSettingOverrideRepository(JpaRuntimeSettingOverrideRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RuntimeSettingOverrideNode> findById(String key) {
        return repository.findById(key).map(this::toDomain);
    }

    @Override
    public RuntimeSettingOverrideNode save(RuntimeSettingOverrideNode override) {
        Instant now = Instant.now();
        RuntimeSettingOverrideEntity entity = toEntity(override);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(now);
        }
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public void deleteById(String key) {
        repository.deleteById(key);
        repository.flush();
    }

    private RuntimeSettingOverrideEntity toEntity(RuntimeSettingOverrideNode source) {
        RuntimeSettingOverrideEntity target = new RuntimeSettingOverrideEntity();
        target.setKey(source.getKey());
        target.setValue(source.getValue());
        target.setLifecycleState(source.getLifecycleState());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }

    private RuntimeSettingOverrideNode toDomain(RuntimeSettingOverrideEntity source) {
        RuntimeSettingOverrideNode target = new RuntimeSettingOverrideNode();
        target.setKey(source.getKey());
        target.setValue(source.getValue());
        target.setLifecycleState(source.getLifecycleState());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getVersion());
        return target;
    }
}
