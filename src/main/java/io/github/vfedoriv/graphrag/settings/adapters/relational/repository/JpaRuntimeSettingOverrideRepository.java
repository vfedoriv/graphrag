package io.github.vfedoriv.graphrag.settings.adapters.relational.repository;

import io.github.vfedoriv.graphrag.settings.adapters.relational.entity.RuntimeSettingOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRuntimeSettingOverrideRepository
    extends JpaRepository<RuntimeSettingOverrideEntity, String> {
}
