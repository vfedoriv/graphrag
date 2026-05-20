package io.github.vfedoriv.graphrag.service;

final class GraphExtractionCleanupSupport {

    private GraphExtractionCleanupSupport() {
    }

    static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    static String toNonBlankErrorMessage(Exception ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }
}
