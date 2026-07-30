package io.github.vfedoriv.graphrag;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import io.github.vfedoriv.graphrag.service.AiProfileService;

final class SharedIntegrationStateExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (!context.getRequiredTestClass().isAnnotationPresent(SpringBootTest.class)) {
            return;
        }
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        JdbcTemplate jdbcTemplate = applicationContext.getBeanProvider(JdbcTemplate.class).getIfAvailable();
        if (jdbcTemplate == null) {
            return;
        }
        Neo4jClient neo4jClient = applicationContext.getBeanProvider(Neo4jClient.class).getIfAvailable();
        if (neo4jClient == null) {
            RelationalMetadataTestCleaner.clean(jdbcTemplate);
            TestDocumentStorage.clean();
        } else {
            IntegrationTestLifecycle.reset(jdbcTemplate, neo4jClient);
        }
        if (!context.getRequiredTestClass().equals(SettingsAndAiProfileRelationalIntegrationTest.class)) {
            jdbcTemplate.update("DELETE FROM app.ai_profile");
            AiProfileService aiProfileService =
                applicationContext.getBeanProvider(AiProfileService.class).getIfAvailable();
            if (aiProfileService != null) {
                aiProfileService.seedDefaultProfile();
            }
        }
    }
}
