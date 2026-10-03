package io.github.vfedoriv.graphrag.schemas.drafts.adapters.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftBinaryStorageAdapterTest {
    @TempDir
    Path tempDir;

    private final BinaryStorageService storage = mock(BinaryStorageService.class);
    private final DraftBinaryStorageAdapter adapter = new DraftBinaryStorageAdapter(storage);

    @Test
    void storesDraftSourceWithUnchangedNamespaceArgumentsAndPropagatesIOException() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3};
        URI expectedUri = URI.create("file:///drafts/kb-1/draft-2/source-3-notes.txt");
        when(storage.storeDraftSource("kb-1", "draft-2", "source-3", "notes.txt", bytes))
            .thenReturn(expectedUri);

        assertThat(adapter.storeDraftSource("kb-1", "draft-2", "source-3", "notes.txt", bytes))
            .isEqualTo(expectedUri);
        verify(storage).storeDraftSource("kb-1", "draft-2", "source-3", "notes.txt", bytes);

        IOException failure = new IOException("storage unavailable");
        when(storage.storeDraftSource("kb-1", "draft-2", "source-4", "notes.txt", bytes))
            .thenThrow(failure);

        assertThatThrownBy(() -> adapter.storeDraftSource("kb-1", "draft-2", "source-4", "notes.txt", bytes))
            .isSameAs(failure);
    }

    @Test
    void delegatesReadAndDeleteAndPropagatesIoExceptions() throws Exception {
        URI contentUri = URI.create("file:///drafts/kb-1/draft-2/source-3-notes.txt");
        InputStream expectedStream = new ByteArrayInputStream(new byte[] {4, 5});
        when(storage.read(contentUri)).thenReturn(expectedStream);

        assertThat(adapter.read(contentUri)).isSameAs(expectedStream);
        adapter.delete(contentUri);

        IOException readFailure = new IOException("read failed");
        when(storage.read(URI.create("file:///drafts/read-failure"))).thenThrow(readFailure);
        assertThatThrownBy(() -> adapter.read(URI.create("file:///drafts/read-failure")))
            .isSameAs(readFailure);

        IOException deleteFailure = new IOException("delete failed");
        doThrow(deleteFailure).when(storage).delete(URI.create("file:///drafts/delete-failure"));
        assertThatThrownBy(() -> adapter.delete(URI.create("file:///drafts/delete-failure")))
            .isSameAs(deleteFailure);
        verify(storage).delete(contentUri);
    }

    @Test
    void checksExistenceThroughTheResolvedPath() throws IOException {
        Path existingPath = Files.writeString(tempDir.resolve("existing.bin"), "draft");
        Path missingPath = tempDir.resolve("missing.bin");
        URI existingUri = existingPath.toUri();
        URI missingUri = missingPath.toUri();
        when(storage.resolvePath(existingUri)).thenReturn(existingPath);
        when(storage.resolvePath(missingUri)).thenReturn(missingPath);

        assertThat(adapter.exists(existingUri)).isTrue();
        assertThat(adapter.exists(missingUri)).isFalse();
        verify(storage).resolvePath(existingUri);
        verify(storage).resolvePath(missingUri);
    }
}
