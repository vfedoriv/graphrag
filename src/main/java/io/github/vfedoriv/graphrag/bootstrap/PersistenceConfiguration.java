package io.github.vfedoriv.graphrag.bootstrap;

import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity.SchemaDraftEvaluationRunEntity;
import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.repository.JpaSchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.entity.SchemaDraftPublicationEntity;
import io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.repository.JpaSchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingPlanEntity;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.repository.JpaSchemaReprocessingPlanRepository;
import jakarta.persistence.EntityManagerFactory;
import io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity.AdvancedSearchRunEntity;
import io.github.vfedoriv.graphrag.search.runs.adapters.relational.repository.JpaAdvancedSearchRunRepository;
import io.github.vfedoriv.graphrag.documents.adapters.graph.repository.Neo4jDocumentChunkRepository;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentUploadEntity;
import io.github.vfedoriv.graphrag.documents.adapters.relational.repository.JpaDocumentUploadRepository;
import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseSchemaEntity;
import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository.JpaKnowledgeBaseSchemaRepository;
import io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.entity.SchemaDefinitionEntity;
import io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.repository.JpaSchemaDefinitionRepository;

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

import io.github.vfedoriv.graphrag.bootstrap.persistence.GraphRepositoryMarker;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository.JpaSchemaDraftRepository;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EntityScan(basePackageClasses = {io.github.vfedoriv.graphrag.ai.profiles.adapters.relational.entity.AiProfileEntity.class,
    io.github.vfedoriv.graphrag.settings.adapters.relational.entity.RuntimeSettingOverrideEntity.class,
    io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseEntity.class, AdvancedSearchRunEntity.class, DocumentUploadEntity.class,
    KnowledgeBaseSchemaEntity.class, SchemaDefinitionEntity.class, SchemaDraftEntity.class, SchemaDraftEvaluationRunEntity.class, SchemaDraftPublicationEntity.class, SchemaReprocessingPlanEntity.class})
@EnableJpaRepositories(
    basePackageClasses = {io.github.vfedoriv.graphrag.ai.profiles.adapters.relational.repository.JpaAiProfileRepository.class,
        io.github.vfedoriv.graphrag.settings.adapters.relational.repository.JpaRuntimeSettingOverrideRepository.class,
        io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository.JpaKnowledgeBaseRepository.class, JpaAdvancedSearchRunRepository.class, JpaDocumentUploadRepository.class,
        JpaKnowledgeBaseSchemaRepository.class, JpaSchemaDefinitionRepository.class, JpaSchemaDraftRepository.class, JpaSchemaDraftEvaluationRunRepository.class, JpaSchemaDraftPublicationRepository.class, JpaSchemaReprocessingPlanRepository.class},
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
@EnableNeo4jRepositories(
    basePackageClasses = {GraphRepositoryMarker.class, Neo4jDocumentChunkRepository.class},
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
