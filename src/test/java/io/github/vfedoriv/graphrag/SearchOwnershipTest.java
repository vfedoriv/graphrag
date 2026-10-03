package io.github.vfedoriv.graphrag;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SearchOwnershipTest {
    @Test
    void queryAndAdvancedSearchHaveOneOwner() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.github.vfedoriv.graphrag");
        assertThat(classes.stream()
            .filter(type -> type.getSimpleName().equals("QueryController")
                || type.getSimpleName().equals("AdvancedSearchRunController")
                || type.getSimpleName().equals("CypherValidationService")
                || type.getSimpleName().equals("AdvancedSearchRunService"))
            .map(type -> type.getPackageName()).toList())
            .hasSize(4).allMatch(name -> name.startsWith("io.github.vfedoriv.graphrag.search."));
    }
}
