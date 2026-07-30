package io.github.vfedoriv.graphrag;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@Import({PostgresTestcontainersConfiguration.class, Neo4jTestcontainersConfiguration.class})
class TestcontainersConfiguration {
}
