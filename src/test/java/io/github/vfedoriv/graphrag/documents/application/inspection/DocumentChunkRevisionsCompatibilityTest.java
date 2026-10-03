package io.github.vfedoriv.graphrag.documents.application.inspection;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentChunkRevisions.Settings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkRevisionsCompatibilityTest {
    @ParameterizedTest
    @CsvSource({
        "fixed-character,800,chunker_90f8623181a107646d5a2c6a71d14b11122d6bf95a218e5b0ca6daf450ada2c9",
        "recursive,800,chunker_bd2b9eadf77307901aac122ed13d07d66d5d5b1773f997c904041f9ae82f654d",
        "fixed-character,900,chunker_67afd8ef7860821bf2f1b2dadf90282693749d2c604ff63ac5dc025432351436"
    })
    void suppliedSnapshotRetainsHistoricalRevisionBytes(String strategy, int targetTokens, String expected) {
        Settings supplied = new Settings(strategy, targetTokens, 80, 4000, targetTokens * 2, 8000, 2, 64, 256, "representation-v1");
        assertThat(new DocumentChunkRevisionsFacade().calculate(supplied)).isEqualTo(expected);
    }
}
