package io.github.vfedoriv.graphrag.application.settings;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public record RuntimeSettingDefinition(
    String key,
    String category,
    SettingType type,
    Object defaultValue,
    boolean mutable,
    boolean liveApplied,
    boolean sensitive,
    Map<String, Object> constraints,
    Function<Object, Object> parser,
    Function<Object, String> storageMapper,
    Function<Object, Object> displayMapper,
    Consumer<Object> liveApplier,
    UpdateMode updateMode,
    String reason,
    String label,
    String description
) {
    public Object parse(Object value) {
        return parser.apply(value);
    }

    public String toStorage(Object value) {
        return storageMapper.apply(value);
    }

    public Object displayValue(Object value) {
        return displayMapper.apply(value);
    }

    public void applyLive(Object value) {
        if (liveApplied) {
            liveApplier.accept(value);
        }
    }

    public enum SettingType {
        INTEGER,
        BOOLEAN,
        STRING,
        STRING_LIST
    }

    public enum UpdateMode {
        LIVE("live"),
        RESTART_REQUIRED("restart-required"),
        PROFILE_MANAGED("profile-managed"),
        READ_ONLY("read-only"),
        SENSITIVE_READ_ONLY("sensitive-read-only");

        private final String apiValue;

        UpdateMode(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }
    }
}
