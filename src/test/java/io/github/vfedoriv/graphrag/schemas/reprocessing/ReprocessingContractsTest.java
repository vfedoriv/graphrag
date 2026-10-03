package io.github.vfedoriv.graphrag.schemas.reprocessing;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReprocessingContractsTest {
    private static final List<String> CONTRACTS = List.of(
        "io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor",
        "io.github.vfedoriv.graphrag.documents.contracts.DocumentReprocessing");

    @Test
    void executionContractsProvideClosedTargetsAndImmutableOptionBags() throws Exception {
        for (String name : CONTRACTS) {
            assertThatCode(() -> Class.forName(name)).doesNotThrowAnyException();
            Class<?> target = Class.forName(name + "$Target");
            assertThat(target.isSealed()).isTrue();
            assertThat(target.getPermittedSubclasses()).hasSize(2);
            Class<?> activation = Class.forName(name + "$Activation");
            List<String> mutableList = new ArrayList<>(List.of("captured"));
            Map<String, Object> options = new HashMap<>(Map.of("nested", mutableList));
            Object value = activation.getConstructor(Map.class).newInstance(options);
            options.clear();
            mutableList.clear();
            Map<?, ?> captured = (Map<?, ?>) activation.getMethod("processingOptions").invoke(value);
            assertThat(captured.get("nested")).isEqualTo(List.of("captured"));
            assertThatThrownBy(captured::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(((List<?>) captured.get("nested"))::clear)
                .isInstanceOf(UnsupportedOperationException.class);
            Class<?> migration = Class.forName(name + "$Migration");
            assertThat(migration.isRecord()).isTrue();
            assertThat(java.util.Arrays.stream(migration.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("aiProfileId", "aiProfileRevision", "embeddingSpaceId", "schemaId",
                    "schemaContentHash", "chunkTarget", "documentTarget");
            assertThatThrownBy(() -> migration.getConstructors()[0].newInstance(
                "profile", 3L, "space", "schema", "hash", null, null))
                .hasCauseInstanceOf(NullPointerException.class);
            Class<?> chunk = Class.forName(name + "$ChunkTarget");
            Object chunkValue = chunk.getConstructors()[0].newInstance(
                "recursive", "strategy-revision", 800, 80, 4000, 2400, 12000, 3, 40, 200,
                "tokenizer", "tokenizer-revision", "EXACT", "representation", "settings-hash");
            assertRequiredFields(chunkValue, "strategyName", "strategyRevision", "tokenizerId",
                "tokenizerRevision", "tokenCountMode", "representationRevision", "settingsHash");
            Class<?> document = Class.forName(name + "$DocumentTarget");
            Map<String, Object> nested = new HashMap<>(Map.of("captured", 7));
            Map<String, Object> effective = new HashMap<>(Map.of("nested", nested));
            Object documentValue = document.getConstructors()[0].newInstance(
                "hash", "text", "parser-revision", "TXT", "chunker-revision", effective);
            nested.clear();
            effective.clear();
            Map<?, ?> saved = (Map<?, ?>) document.getMethod("effectiveProcessingOptions").invoke(documentValue);
            assertThat(saved.get("nested")).isEqualTo(Map.of("captured", 7));
            assertThatThrownBy(((Map<?, ?>) saved.get("nested"))::clear)
                .isInstanceOf(UnsupportedOperationException.class);
            assertRequiredFields(documentValue, "sourceSha256", "parserId", "parserRevision",
                "fileFormat", "effectiveChunkerRevision", "effectiveProcessingOptions");
            Object migrationValue = migration.getConstructors()[0].newInstance(
                "profile", 3L, "space", "schema", "hash", chunkValue, documentValue);
            assertRequiredFields(migrationValue, "aiProfileId", "embeddingSpaceId", "schemaId",
                "schemaContentHash", "chunkTarget", "documentTarget");
        }
    }

    @Test
    void preparationContractsExistWithoutProviderSecrets() throws Exception {
        for (String name : List.of(
            "io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentPreparation",
            "io.github.vfedoriv.graphrag.documents.contracts.DocumentMigrationPreparation")) {
            Class<?> contract = Class.forName(name);
            assertThat(contract.isInterface()).isTrue();
            Class<?> profile = Class.forName(name + "$Profile");
            assertThat(Arrays.stream(profile.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "revision", "baseUrl", "embeddingModel", "embeddingDimensions", "tokenizerId");
        }
    }

    private void assertRequiredFields(Object value, String... fields) throws Exception {
        RecordComponent[] components = value.getClass().getRecordComponents();
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            arguments[index] = components[index].getAccessor().invoke(value);
        }
        for (int index = 0; index < components.length; index++) {
            if (Arrays.asList(fields).contains(components[index].getName())) {
                Object[] missing = arguments.clone();
                missing[index] = null;
                assertThatThrownBy(() -> value.getClass().getConstructors()[0].newInstance(missing))
                    .as("required field %s", components[index].getName())
                    .hasCauseInstanceOf(NullPointerException.class);
            }
        }
    }
}
