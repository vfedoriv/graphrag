package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentSourceInputs;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryDocumentInputs;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class DiscoveryDocumentInputsAdapterTest {
    @Test
    void mapsSourceWithoutExposingMutableBytesOrAddingTransactions() {
        DocumentSourceInputs documents = mock(DocumentSourceInputs.class);
        byte[] bytes = new byte[] {1, 2};
        when(documents.readOwned("kb", "doc"))
            .thenReturn(new DocumentSourceInputs.Source("doc", "doc.txt", "text/plain", bytes));

        DiscoveryDocumentInputs.Source source = new DiscoveryDocumentInputsAdapter(documents)
            .readOwned("kb", "doc");
        bytes[0] = 9;
        source.bytes()[1] = 9;

        assertThat(source.bytes()).containsExactly((byte) 1, (byte) 2);
        assertThat(source.byteCount()).isEqualTo(2);
        assertThat(DiscoveryDocumentInputsAdapter.class.isAnnotationPresent(Transactional.class)).isFalse();
    }
}
