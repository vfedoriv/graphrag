package io.github.vfedoriv.graphrag.bootstrap.integration.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.contracts.CapturedSchemaParsing;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchSchemaAdapterTest {
    private final StoredSchemaSnapshots stored = mock(StoredSchemaSnapshots.class);
    private final SchemaSnapshots active = mock(SchemaSnapshots.class);
    private final CapturedSchemaParsing captured = mock(CapturedSchemaParsing.class);
    private final SearchSchemaAdapter adapter = new SearchSchemaAdapter(stored, active, captured);

    @Test
    void availabilityChecksStoredExistenceWithoutParsing() {
        when(stored.findById("schema-1")).thenReturn(Optional.of(snapshot("schema-1", "invalid json")));
        when(stored.findById("missing")).thenReturn(Optional.empty());

        assertThat(adapter.available("schema-1")).isTrue();
        assertThat(adapter.available("missing")).isFalse();

        verify(stored).findById("schema-1");
        verify(stored).findById("missing");
        verifyNoInteractions(active, captured);
    }

    @Test
    void captureReturnsTheRawStoredSnapshotAndUsesSchemaNotFoundMessage() {
        SchemaSnapshot storedSnapshot = snapshot("schema-1", "{\"name\":\"stored\",\"version\":1}");
        when(stored.findById("schema-1")).thenReturn(Optional.of(storedSnapshot));
        when(stored.findById("missing")).thenReturn(Optional.empty());

        assertThat(adapter.capture("schema-1")).isSameAs(storedSnapshot);
        assertThatThrownBy(() -> adapter.capture("missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing");
        verifyNoInteractions(active, captured);
    }

    @Test
    void delegatesActiveResolutionAndCapturedParsingWithoutChangingIdentityFacts() {
        SchemaSnapshot activeSnapshot = snapshot("active-schema", "active content");
        SchemaSnapshot capturedSnapshot = snapshot("captured-schema", "captured content");
        when(active.resolveActive("kb-1")).thenReturn(activeSnapshot);
        when(captured.parseCaptured("kb-1", "captured-schema", "captured-hash", "captured content"))
            .thenReturn(capturedSnapshot);

        assertThat(adapter.resolveActive("kb-1")).isSameAs(activeSnapshot);
        assertThat(adapter.parseCaptured("kb-1", "captured-schema", "captured-hash", "captured content"))
            .isSameAs(capturedSnapshot);

        verify(active).resolveActive("kb-1");
        verify(captured).parseCaptured("kb-1", "captured-schema", "captured-hash", "captured content");
    }

    @Test
    void adapterAddsNoRelationalTransactionBoundaryOrPersistenceDependency() {
        assertThat(SearchSchemaAdapter.class.isAnnotationPresent(RelationalTransactional.class)).isFalse();
        assertThat(Arrays.stream(SearchSchemaAdapter.class.getDeclaredMethods())
            .anyMatch(method -> method.isAnnotationPresent(RelationalTransactional.class)))
            .isFalse();
        assertThat(Arrays.stream(SearchSchemaAdapter.class.getDeclaredFields())
            .map(field -> field.getType().getPackageName()))
            .containsOnly("io.github.vfedoriv.graphrag.schemas.contracts");
    }

    private SchemaSnapshot snapshot(String schemaId, String content) {
        return new SchemaSnapshot("kb-1", schemaId, "stored", 1, null, null,
            null, content, "stored-hash", null, null, null);
    }
}
