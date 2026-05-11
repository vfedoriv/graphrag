package io.github.vfedoriv.graphrag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

final class TestDocumentStorage {

    static final Path ROOT = Path.of("target", "test-documents");

    private TestDocumentStorage() {
    }

    static void clean() throws IOException {
        if (!Files.exists(ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
