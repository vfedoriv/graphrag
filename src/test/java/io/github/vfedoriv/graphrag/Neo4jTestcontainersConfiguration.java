package io.github.vfedoriv.graphrag;

import org.neo4j.driver.AuthTokens;
import org.springframework.boot.neo4j.autoconfigure.Neo4jConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.neo4j.Neo4jContainer;

@TestConfiguration(proxyBeanMethods = false)
class Neo4jTestcontainersConfiguration {

    @Bean
    Neo4jConnectionDetails neo4jConnectionDetails() {
        Neo4jContainer container = SharedApplicationIntegrationContainers.neo4j();
        return new Neo4jConnectionDetails() {
            @Override
            public java.net.URI getUri() {
                return java.net.URI.create(container.getBoltUrl());
            }

            @Override
            public org.neo4j.driver.AuthToken getAuthToken() {
                return AuthTokens.basic("neo4j", container.getAdminPassword());
            }
        };
    }
}
