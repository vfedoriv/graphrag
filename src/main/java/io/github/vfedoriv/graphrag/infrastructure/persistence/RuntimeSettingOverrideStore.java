package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.repository.RuntimeSettingOverrideRepository;
import java.util.Optional;

public final class RuntimeSettingOverrideStore {

    private final RuntimeSettingOverrideRepository repository;

    public RuntimeSettingOverrideStore(RuntimeSettingOverrideRepository repository) {
        this.repository = repository;
    }

    public boolean configured() {
        return repository != null;
    }

    public Optional<RuntimeSettingOverrideNode> find(String key) {
        return repository == null ? Optional.empty() : repository.findById(key);
    }

    public RuntimeSettingOverrideNode getOrCreate(String key) {
        requireConfigured();
        return repository.findById(key).orElseGet(() -> {
            RuntimeSettingOverrideNode node = new RuntimeSettingOverrideNode();
            node.setKey(key);
            return node;
        });
    }

    public RuntimeSettingOverrideNode save(RuntimeSettingOverrideNode node) {
        requireConfigured();
        return repository.save(node);
    }

    public void delete(String key) {
        requireConfigured();
        repository.deleteById(key);
    }

    public void backfillMissingVersions() {
        if (repository != null) {
            repository.backfillMissingVersions();
        }
    }

    public void requireConfigured() {
        if (repository == null) {
            throw new IllegalStateException("Runtime setting persistence is not configured");
        }
    }
}
