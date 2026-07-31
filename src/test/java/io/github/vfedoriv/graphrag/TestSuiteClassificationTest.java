package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestSuiteClassificationTest {

    @Test
    void everyDockerBackedIntegrationTestUsesSharedClassification() throws IOException {
        List<Path> integrationTests;
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            integrationTests = files
                .filter(path -> path.getFileName().toString().endsWith("IntegrationTest.java"))
                .filter(path -> !path.getFileName().toString().equals("IntegrationTest.java"))
                .toList();
        }

        assertThat(integrationTests).isNotEmpty();
        assertThat(integrationTests)
            .allSatisfy(path -> assertThat(Files.readString(path))
                .as(path.toString())
                .containsAnyOf(
                    "@IntegrationTest",
                    "@RelationalIntegrationTest",
                    "@FullStoreIntegrationTest"
                ));
    }

    @Test
    void relationalRepositoryContextsUseTheRelationalContextFamily() throws IOException {
        List<Path> relationalTests;
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            relationalTests = files
                .filter(path -> path.getFileName().toString()
                    .endsWith("RelationalRepositoryIntegrationTest.java"))
                .toList();
        }

        assertThat(relationalTests).isNotEmpty();
        assertThat(relationalTests)
            .allSatisfy(path -> assertThat(Files.readString(path))
                .as(path.toString())
                .contains("@RelationalIntegrationTest")
                .doesNotContain(
                    "@Import(TestcontainersConfiguration.class",
                    "    TestcontainersConfiguration.class,"
                ));
    }

    @Test
    void fastProfileExcludesOnlyTheIntegrationTag() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom)
            .contains("<id>fast</id>")
            .contains("<excludedGroups>integration</excludedGroups>");
        assertThat(Files.readString(Path.of(
            "src/test/java/io/github/vfedoriv/graphrag/ArchitectureBoundaryTest.java"
        ))).doesNotContain("@IntegrationTest");
        assertThat(Files.readString(Path.of(
            "src/test/java/io/github/vfedoriv/graphrag/controller/QueryControllerTest.java"
        ))).doesNotContain("@IntegrationTest");
    }
}
