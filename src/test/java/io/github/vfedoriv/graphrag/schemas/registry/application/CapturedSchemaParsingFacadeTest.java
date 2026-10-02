package io.github.vfedoriv.graphrag.schemas.registry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.schemas.contracts.CapturedSchemaParsing;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapturedSchemaParsingFacadeTest {
    private final SchemaParser parser = mock(SchemaParser.class);
    private final CapturedSchemaParsingFacade facade = new CapturedSchemaParsingFacade(parser);

    @Test
    void parsesExactCapturedContentAndKeepsCapturedIdentityAndHash() {
        String knowledgeBaseId = "kb-at-submission";
        String schemaId = "schema-at-submission";
        String contentHash = "hash-at-submission";
        String content = " { \"name\": \"captured\", \"version\": 7 } ";
        SchemaDocument parsed = new SchemaDocument("captured", 7, null, List.of(), List.of(), List.of(), List.of());
        when(parser.parse(content)).thenReturn(parsed);

        SchemaSnapshot snapshot = facade.parseCaptured(knowledgeBaseId, schemaId, contentHash, content);

        assertThat(snapshot.knowledgeBaseId()).isEqualTo(knowledgeBaseId);
        assertThat(snapshot.schemaDefinitionId()).isEqualTo(schemaId);
        assertThat(snapshot.contentHash()).isEqualTo(contentHash);
        assertThat(snapshot.content()).isEqualTo(content);
        assertThat(snapshot.name()).isEqualTo("captured");
        assertThat(snapshot.version()).isEqualTo(7);
        assertThat(snapshot.schema()).isEqualTo(parsed);
        assertThat(snapshot.schema()).isNotSameAs(parsed);
        assertThat(snapshot.sourceType()).isNull();
        assertThat(snapshot.format()).isNull();
        assertThat(snapshot.status()).isNull();
        assertThat(snapshot.createdAt()).isNull();
        assertThat(snapshot.updatedAt()).isNull();
        verify(parser).parse(content);
    }
}
