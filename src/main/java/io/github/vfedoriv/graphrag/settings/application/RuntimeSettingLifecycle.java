package io.github.vfedoriv.graphrag.settings.application;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingDefinition.UpdateMode;
import io.github.vfedoriv.graphrag.settings.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingOverrideStore;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeSettingLifecycle {

    private final RuntimeSettingOverrideStore store;
    private final Map<String, RuntimeSettingDefinition> definitions;
    private final Map<String, Object> restartRequiredActiveValues = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public RuntimeSettingLifecycle(
        RuntimeSettingOverrideStore store,
        Map<String, RuntimeSettingDefinition> definitions
    ) {
        this.store = store;
        this.definitions = definitions;
    }

    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (restartRequiredActiveValues) {
            if (loaded) {
                return;
            }
            store.backfillMissingVersions();
            if (store.configured()) {
                for (RuntimeSettingDefinition definition : definitions.values()) {
                    if (definition.mutable() && definition.updateMode() == UpdateMode.RESTART_REQUIRED) {
                        store.find(definition.key())
                            .map(RuntimeSettingOverrideNode::getValue)
                            .map(definition::parse)
                            .ifPresent(value -> restartRequiredActiveValues.put(definition.key(), value));
                    }
                }
            }
            loaded = true;
        }
    }

    public Object activeParsedValue(RuntimeSettingDefinition definition) {
        if (definition.updateMode() == UpdateMode.RESTART_REQUIRED) {
            return restartRequiredActiveValues.getOrDefault(definition.key(), definition.defaultValue());
        }
        return definition.defaultValue();
    }

    public String state(RuntimeSettingDefinition definition, Object parsedValue) {
        if (definition.updateMode() == UpdateMode.LIVE) {
            return "active";
        }
        if (definition.updateMode() == UpdateMode.RESTART_REQUIRED) {
            Object currentValue = definition.displayValue(parsedValue);
            Object activeValue = definition.displayValue(activeParsedValue(definition));
            return Objects.equals(currentValue, activeValue) ? "active" : "pending-restart";
        }
        return "default";
    }

    public void reconcile(RuntimeSettingOverrideNode override, String lifecycleState) {
        if (override == null || Objects.equals(override.getLifecycleState(), lifecycleState)) {
            return;
        }
        override.setLifecycleState(lifecycleState);
        store.save(override);
    }
}
