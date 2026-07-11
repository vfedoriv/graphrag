package io.github.vfedoriv.graphrag.application.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.logging.LogLevel;

public final class RuntimeSettingCodecs {

    public Integer integer(String key, Object value, int min) {
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(key + " must be an integer", ex);
            }
        }
        if (parsed < min) {
            throw new IllegalArgumentException(key + " must be greater than or equal to " + min);
        }
        return parsed;
    }

    public Boolean bool(String key, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException(key + " must be a boolean");
    }

    public List<String> stringList(String key, Object value) {
        List<String> items = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addListItem(items, item);
            }
        } else {
            for (String item : String.valueOf(value).split(",")) {
                addListItem(items, item);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException(key + " must include at least one value");
        }
        return List.copyOf(items);
    }

    public String nonBlankString(String key, Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return text;
    }

    public String loggingLevel(String key, Object value) {
        String text = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        try {
            LogLevel.valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(key + " must be one of TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF", ex);
        }
        return text;
    }

    public Object masked(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return Map.of("configured", !text.isBlank(), "masked", true);
    }

    @SuppressWarnings("unchecked")
    public List<String> castStringList(Object value) {
        return (List<String>) value;
    }

    private void addListItem(List<String> items, Object item) {
        String text = String.valueOf(item).trim();
        if (!text.isBlank()) {
            items.add(text);
        }
    }
}
