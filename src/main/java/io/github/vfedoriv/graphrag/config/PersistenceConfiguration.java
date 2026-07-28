package io.github.vfedoriv.graphrag.config;

import jakarta.persistence.EntityManagerFactory;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.RelationalEntityMarker;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.RelationalRepositoryMarker;
import io.github.vfedoriv.graphrag.repository.GraphRepositoryMarker;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EntityScan(basePackageClasses = RelationalEntityMarker.class)
@EnableJpaRepositories(
    basePackageClasses = RelationalRepositoryMarker.class,
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
@EnableNeo4jRepositories(
    basePackageClasses = GraphRepositoryMarker.class,
    neo4jTemplateRef = "neo4jTemplate",
    transactionManagerRef = "neo4jTransactionManager"
)
public class PersistenceConfiguration {

    @Bean(name = "transactionManager")
    @Primary
    PlatformTransactionManager transactionManager(
        EntityManagerFactory entityManagerFactory,
        ObjectProvider<TransactionManagerCustomizers> customizers
    ) {
        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        customizers.ifAvailable(availableCustomizers -> availableCustomizers.customize(transactionManager));
        return transactionManager;
    }

    @Bean(name = "neo4jTransactionManager")
    Neo4jTransactionManager neo4jTransactionManager(
        Driver driver,
        DatabaseSelectionProvider databaseSelectionProvider,
        ObjectProvider<TransactionManagerCustomizers> customizers
    ) {
        Neo4jTransactionManager transactionManager = new Neo4jTransactionManager(driver, databaseSelectionProvider);
        customizers.ifAvailable(availableCustomizers -> availableCustomizers.customize(transactionManager));
        return transactionManager;
    }

    @Bean(name = "neo4jTemplate")
    Neo4jTemplate neo4jTemplate(
        Neo4jClient neo4jClient,
        Neo4jMappingContext neo4jMappingContext,
        @Qualifier("neo4jTransactionManager") PlatformTransactionManager transactionManager
    ) {
        return new Neo4jTemplate(neo4jClient, neo4jMappingContext, transactionManager);
    }
}
