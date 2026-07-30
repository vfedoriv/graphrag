package io.github.vfedoriv.graphrag;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

final class SharedApplicationIntegrationContainers {

    private static final Object MONITOR = new Object();
    private static PostgreSQLContainer<?> postgres;
    private static Neo4jContainer neo4j;
    private static boolean shutdownHookRegistered;

    private SharedApplicationIntegrationContainers() {
    }

    static PostgreSQLContainer<?> postgres() {
        synchronized (MONITOR) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
                    .withDatabaseName("graphrag")
                    .withUsername("graphrag")
                    .withPassword("graphrag-test-password");
                postgres.start();
                registerShutdownHook();
                reportStart("postgresql", postgres.getContainerId());
            }
            return postgres;
        }
    }

    static Neo4jContainer neo4j() {
        synchronized (MONITOR) {
            if (neo4j == null) {
                neo4j = new Neo4jContainer(DockerImageName.parse("neo4j:5.26.25"));
                neo4j.start();
                registerShutdownHook();
                reportStart("neo4j", neo4j.getContainerId());
            }
            return neo4j;
        }
    }

    static String postgresIdentity() {
        return postgres().getContainerId();
    }

    static String neo4jIdentity() {
        return neo4j().getContainerId();
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(
            SharedApplicationIntegrationContainers::stop,
            "graphrag-testcontainers-shutdown"
        ));
        shutdownHookRegistered = true;
    }

    private static void stop() {
        synchronized (MONITOR) {
            if (neo4j != null) {
                neo4j.stop();
                neo4j = null;
            }
            if (postgres != null) {
                postgres.stop();
                postgres = null;
            }
        }
    }

    private static void reportStart(String kind, String containerId) {
        System.out.printf(
            "GRAPHRAG_TEST_CONTAINER_START kind=%s scope=application id=%s%n",
            kind,
            containerId
        );
    }
}
