package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import java.util.Optional;

public interface RuntimeSettingOverrideRepository {

    Optional<RuntimeSettingOverrideNode> findById(String key);

    RuntimeSettingOverrideNode save(RuntimeSettingOverrideNode override);

    void deleteById(String key);

    default Long backfillMissingVersions() {
        return 0L;
    }
}
