package io.github.vfedoriv.graphrag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "app.neo4j.initialize-schema=false")
@Import(PostgresTestcontainersConfiguration.class)
@IntegrationTest
public @interface RelationalIntegrationTest {
}
