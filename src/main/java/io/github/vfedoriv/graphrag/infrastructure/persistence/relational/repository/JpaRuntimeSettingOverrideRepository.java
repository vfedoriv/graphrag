package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.RuntimeSettingOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRuntimeSettingOverrideRepository
    extends JpaRepository<RuntimeSettingOverrideEntity, String> {
}
