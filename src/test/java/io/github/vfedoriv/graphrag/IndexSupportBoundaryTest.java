package io.github.vfedoriv.graphrag;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import io.github.vfedoriv.graphrag.documents.architecture.IndexDependencyFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class IndexSupportBoundaryTest {
    @Test
    void foreignFeaturesUseIndexContractsInsteadOfGraphImplementations() {
        JavaClasses classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.github.vfedoriv.graphrag");
        noClasses().that().resideInAnyPackage("..documents..", "..search..")
            .should().dependOnClassesThat().resideInAnyPackage("..indexes.adapters..")
            .check(classes);
        noClasses().that().resideInAnyPackage("..documents..", "..search..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                "io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService")
            .check(classes);
    }

    @Test
    void boundaryRejectsImplementationNeighborAndAllowsPublicContract() {
        assertThat(noClasses().that().resideInAnyPackage("..documents..", "..search..")
            .should().dependOnClassesThat().resideInAPackage("..indexes.adapters..")
            .evaluate(new ClassFileImporter().importClasses(IndexDependencyFixtures.ForbiddenImplementation.class))
            .hasViolation()).isTrue();
        assertThat(noClasses().that().resideInAnyPackage("..documents..", "..search..")
            .should().dependOnClassesThat().resideInAPackage("..indexes.adapters..")
            .evaluate(new ClassFileImporter().importClasses(IndexDependencyFixtures.AllowedContract.class))
            .hasViolation()).isFalse();
    }

    @Test
    void indexSupportDoesNotInterpretFeatureState() {
        JavaClasses classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.github.vfedoriv.graphrag");
        noClasses().that().resideInAPackage("..indexes..")
            .should().dependOnClassesThat().resideInAnyPackage("..documents..", "..search..", "..schemas..")
            .allowEmptyShould(true).check(classes);
    }
}
