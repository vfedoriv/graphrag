package io.github.vfedoriv.graphrag.ai;

import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;

import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;

import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;

import io.github.vfedoriv.graphrag.ai.adapters.provider.AiRuntimeModelFactory;

import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AiKnowledgeBaseBoundaryTest {
    @Test
    void businessConsumersReadPublicFactsInsteadOfForeignMutableState() {
        Set<String> forbidden = Set.of("AiProfileNode", "AiProfileService", "KnowledgeBaseNode",
            "KnowledgeBaseService", "KnowledgeBaseLifecycleService", "AiRuntimeModelFactory");
        Set<String> violations = new TreeSet<>();
        for (JavaClass consumer : new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.github.vfedoriv.graphrag")) {
            String name = consumer.getPackageName();
            if (!(name.contains(".documents.") || name.contains(".search.")
                || name.contains(".schemas."))) continue;
            consumer.getDirectDependenciesFromSelf().stream()
                .filter(edge -> forbidden.contains(edge.getTargetClass().getSimpleName()))
                .forEach(edge -> violations.add(edge.getOriginClass().getName() + " -> " + edge.getTargetClass().getName()));
        }
        assertThat(violations).isEmpty();
    }
}
