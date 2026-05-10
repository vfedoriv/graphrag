package io.github.vfedoriv.graphrag.logging;

public final class LogSanitizer {

    private static final int DEFAULT_PREVIEW_LENGTH = 300;

    private LogSanitizer() {
    }

    public static int length(String value) {
        return value == null ? 0 : value.length();
    }

    public static String preview(String value) {
        return preview(value, DEFAULT_PREVIEW_LENGTH);
    }

    public static String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        return singleLine.substring(0, Math.max(0, maxLength)) + "...";
    }

    public static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
