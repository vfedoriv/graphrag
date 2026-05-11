package io.github.vfedoriv.graphrag.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.config.AppProperties;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemBinaryStorageServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void generatesFileUriAndReadsBackBytes() throws Exception {
        AppProperties props = new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("http://localhost", "k", "m1", 10, "m2"),
            new AppProperties.Storage(tempDir),
            new AppProperties.Chunking(1, 0, 1),
            new AppProperties.Query(1, 1, true, java.util.List.of("CREATE")),
            new AppProperties.Extraction(1, 1, 0)
        );
        LocalFilesystemBinaryStorageService storage = new LocalFilesystemBinaryStorageService(props);

        URI uri = storage.store("kb-1", "doc-1", "my doc.txt", "abc".getBytes());
        assertThat(uri.toString()).startsWith("file:");
        assertThat(storage.resolvePath(uri)).exists();
        assertThat(Files.readString(storage.resolvePath(uri))).isEqualTo("abc");

        try (InputStream stream = storage.read(uri)) {
            assertThat(new String(stream.readAllBytes())).isEqualTo("abc");
        }
    }
}
