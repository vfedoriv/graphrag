package io.github.vfedoriv.graphrag;

import java.util.Collection;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import io.github.vfedoriv.graphrag.service.AiProfileService;

final class SharedIntegrationStateExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (!isSpringBootTest(context)) {
            return;
        }
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        reset(applicationContext, context);
        JdbcTemplate jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
        if (!context.getRequiredTestClass().equals(SettingsAndAiProfileRelationalIntegrationTest.class)) {
            resetDefaultAiProfile(applicationContext, jdbcTemplate);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (!isSpringBootTest(context)) {
            return;
        }
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        reset(applicationContext, context);
        resetDefaultAiProfile(applicationContext, applicationContext.getBean(JdbcTemplate.class));
    }

    private boolean isSpringBootTest(ExtensionContext context) {
        return AnnotatedElementUtils.hasAnnotation(
            context.getRequiredTestClass(),
            SpringBootTest.class
        );
    }

    private void reset(
        ApplicationContext applicationContext,
        ExtensionContext context
    ) throws Exception {
        JdbcTemplate jdbcTemplate = applicationContext.getBeanProvider(JdbcTemplate.class).getIfAvailable();
        if (jdbcTemplate == null) {
            return;
        }
        boolean relationalOnly = AnnotatedElementUtils.hasAnnotation(
            context.getRequiredTestClass(),
            RelationalIntegrationTest.class
        );
        Neo4jClient neo4jClient = relationalOnly
            ? null
            : applicationContext.getBean(Neo4jClient.class);
        Collection<IntegrationTestLifecycle.ResettableTestDouble> resettableTestDoubles =
            applicationContext.getBeansOfType(
                IntegrationTestLifecycle.ResettableTestDouble.class
            ).values();
        IntegrationTestLifecycle.reset(
            jdbcTemplate,
            neo4jClient,
            resettableTestDoubles.toArray(IntegrationTestLifecycle.ResettableTestDouble[]::new)
        );
    }

    private void resetDefaultAiProfile(
        ApplicationContext applicationContext,
        JdbcTemplate jdbcTemplate
    ) {
        jdbcTemplate.update("DELETE FROM app.ai_profile");
        AiProfileService aiProfileService =
            applicationContext.getBeanProvider(AiProfileService.class).getIfAvailable();
        if (aiProfileService != null) {
            aiProfileService.seedDefaultProfile();
        }
    }
}
