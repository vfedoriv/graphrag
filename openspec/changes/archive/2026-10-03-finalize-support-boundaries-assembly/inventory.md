## Step-nine implementation inventory

Baseline: `graphrag` at `f4733eb`. Each row below is an exact baseline dependency, not a permanent allowance. All 179 freezes are removed; each row records its final permitted role/capability and enforcing rule.

### Affected legacy classes

| Existing class | Final owner / surface |
|---|---|
| `application.schema.SchemaGenerationPromptFactory` | `schemas.generation.domain.SchemaGenerationPromptFactory` |
| `application.schema.SchemaGenerationWarningFactory` | `schemas.generation.domain.SchemaGenerationWarningFactory` |
| `application.schema.SchemaGraphMapper` | `schemas.generation.domain.SchemaGraphMapper` |
| `application.settings.RuntimeSettingCodecs` | `settings.application.RuntimeSettingCodecs` |
| `application.settings.RuntimeSettingDefinition` | `settings.application.RuntimeSettingDefinition` |
| `application.settings.RuntimeSettingLifecycle` | `settings.application.RuntimeSettingLifecycle` |
| `application.settings.RuntimeSettingLiveAppliers` | `settings.application.RuntimeSettingLiveAppliers` |
| `application.settings.RuntimeSettingsCatalog` | `settings.application.RuntimeSettingsCatalog` |
| `config.AdvancedSearchConfiguration` | `bootstrap.AdvancedSearchConfiguration` |
| `config.AdvancedSearchProperties` | `search.runs.configuration.AdvancedSearchProperties` |
| `config.AiObservabilityProperties` | `observability.configuration.AiObservabilityProperties` |
| `config.AiObservationConfiguration` | `bootstrap.AiObservationConfiguration` |
| `config.AppProperties` | `bootstrap.AppProperties` |
| `config.LegacyJacksonConfiguration` | `bootstrap.LegacyJacksonConfiguration` |
| `config.OpenApiConfig` | `bootstrap.OpenApiConfig` |
| `config.PersistenceConfiguration` | `bootstrap.PersistenceConfiguration` |
| `config.SchemaDraftAnalysisConfiguration` | `bootstrap.SchemaDraftAnalysisConfiguration` |
| `config.SchemaDraftAnalysisProperties` | `schemas.drafts.configuration.SchemaDraftAnalysisProperties` |
| `controller.AiProfileController` | `ai.profiles.api.AiProfileController` |
| `controller.ChunkMigrationController` | `schemas.reprocessing.api.ChunkMigrationController` |
| `controller.KnowledgeBaseController` | `knowledgebase.api.KnowledgeBaseController` |
| `controller.RuntimeSettingsController` | `settings.api.RuntimeSettingsController` |
| `controller.SchemaController` | `schemas.registry.api.SchemaController` |
| `documents.domain.chunking.TokenizerId` | `ai.domain.TokenizerId` |
| `domain.AiProfileNode` | `ai.profiles.domain.AiProfileNode` |
| `domain.DiffBaselineType` | `schemas.contracts.DiffBaselineType` |
| `domain.KnowledgeBaseNode` | `knowledgebase.domain.KnowledgeBaseNode` |
| `domain.RuntimeSettingOverrideNode` | `settings.domain.RuntimeSettingOverrideNode` |
| `domain.SchemaFormat` | `schemas.contracts.SchemaFormat` |
| `domain.SchemaSourceType` | `schemas.contracts.SchemaSourceType` |
| `domain.SchemaStatus` | `schemas.contracts.SchemaStatus` |
| `dto.AiProfileResponse` | `ai.profiles.api.model.AiProfileResponse` |
| `dto.BaseRequest` | `http.contracts.BaseRequest` |
| `dto.BulkUpdateRuntimeSettingsRequest` | `settings.api.model.BulkUpdateRuntimeSettingsRequest` |
| `dto.CreateAiProfileRequest` | `ai.profiles.api.model.CreateAiProfileRequest` |
| `dto.CreateKnowledgeBaseRequest` | `knowledgebase.api.model.CreateKnowledgeBaseRequest` |
| `dto.CreateSchemaRequest` | `schemas.registry.api.model.CreateSchemaRequest` |
| `dto.GenerateSchemaExampleRequest` | `schemas.generation.api.model.GenerateSchemaExampleRequest` |
| `dto.GenerateSchemaExampleResponse` | `schemas.generation.api.model.GenerateSchemaExampleResponse` |
| `dto.GenerateSchemaFromFileRequest` | `schemas.generation.api.model.GenerateSchemaFromFileRequest` |
| `dto.GenerateSchemaRequest` | `schemas.generation.api.model.GenerateSchemaRequest` |
| `dto.GenerateSchemaResponse` | `schemas.generation.api.model.GenerateSchemaResponse` |
| `dto.IdResponse` | `http.contracts.IdResponse` |
| `dto.KnowledgeBaseResponse` | `knowledgebase.api.model.KnowledgeBaseResponse` |
| `dto.PageResponse` | `http.contracts.PageResponse` |
| `dto.RuntimeSettingResponse` | `settings.api.model.RuntimeSettingResponse` |
| `dto.RuntimeSettingUpdateRequest` | `settings.api.model.RuntimeSettingUpdateRequest` |
| `dto.SchemaDetailsResponse` | `schemas.registry.api.model.SchemaDetailsResponse` |
| `dto.SchemaDiscoveryRequest` | `schemas.discovery.api.model.SchemaDiscoveryRequest` |
| `dto.SchemaDiscoveryResponse` | `schemas.discovery.api.model.SchemaDiscoveryResponse` |
| `dto.SchemaGenerationResult` | `schemas.generation.domain.SchemaGenerationResult` |
| `dto.SchemaGenerationWarning` | `schemas.generation.domain.SchemaGenerationWarning` |
| `dto.SchemaResponse` | `schemas.registry.api.model.SchemaResponse` |
| `dto.SchemaValidationResponse` | `schemas.registry.api.model.SchemaValidationResponse` |
| `dto.UpdateAiProfileRequest` | `ai.profiles.api.model.UpdateAiProfileRequest` |
| `dto.UpdateKnowledgeBaseAiProfileRequest` | `knowledgebase.api.model.UpdateKnowledgeBaseAiProfileRequest` |
| `dto.UpdateKnowledgeBaseRequest` | `knowledgebase.api.model.UpdateKnowledgeBaseRequest` |
| `dto.UpdateRuntimeSettingRequest` | `settings.api.model.UpdateRuntimeSettingRequest` |
| `dto.UpdateSchemaRequest` | `schemas.registry.api.model.UpdateSchemaRequest` |
| `dto.ValidateSchemaRequest` | `schemas.registry.api.model.ValidateSchemaRequest` |
| `embedding.EmbeddingClient` | `ai.models.EmbeddingClient` |
| `embedding.SpringAiEmbeddingClient` | `ai.adapters.provider.SpringAiEmbeddingClient` |
| `error.ConflictException` | `http.contracts.ConflictException` |
| `error.EmbeddingSpaceConflictException` | `ai.api.error.EmbeddingSpaceConflictException` |
| `error.GlobalExceptionHandler` | `bootstrap.http.GlobalExceptionHandler` |
| `error.GraphExtractionValidationException` | `documents.api.error.GraphExtractionValidationException` |
| `error.KnowledgeBaseNotEmptyException` | `knowledgebase.api.error.KnowledgeBaseNotEmptyException` |
| `error.NotFoundException` | `http.contracts.NotFoundException` |
| `error.ProcessingOptionsValidationException` | `documents.api.error.ProcessingOptionsValidationException` |
| `error.SchemaDiscoveryFailedException` | `schemas.discovery.api.error.SchemaDiscoveryFailedException` |
| `graph.LLMGraphTransformerExt` | `schemas.generation.adapters.model.LLMGraphTransformerExt` |
| `infrastructure.ai.ProfileScopedAiClientResolver` | `ai.models.ProfileScopedAiClientResolver` |
| `infrastructure.ai.SchemaGenerationModelAdapter` | `schemas.generation.adapters.model.SchemaGenerationModelAdapter` |
| `infrastructure.persistence.Neo4jLexicalIndexRepository` | `indexes.adapters.graph.Neo4jLexicalIndexRepository` |
| `infrastructure.persistence.RuntimeSettingOverrideStore` | `settings.application.RuntimeSettingOverrideStore` |
| `infrastructure.persistence.graph.GraphSchemaInitializer` | `bootstrap.GraphSchemaInitializer` |
| `infrastructure.persistence.relational.RelationalAiProfileRepository` | `ai.profiles.adapters.relational.RelationalAiProfileRepository` |
| `infrastructure.persistence.relational.RelationalKnowledgeBaseRepository` | `knowledgebase.adapters.relational.RelationalKnowledgeBaseRepository` |
| `infrastructure.persistence.relational.RelationalRuntimeSettingOverrideRepository` | `settings.adapters.relational.RelationalRuntimeSettingOverrideRepository` |
| `infrastructure.persistence.relational.entity.AiProfileEntity` | `ai.profiles.adapters.relational.entity.AiProfileEntity` |
| `infrastructure.persistence.relational.entity.KnowledgeBaseEntity` | `knowledgebase.adapters.relational.entity.KnowledgeBaseEntity` |
| `infrastructure.persistence.relational.entity.RelationalEntityMarker` | `bootstrap.persistence.RelationalEntityMarker` |
| `infrastructure.persistence.relational.entity.RuntimeSettingOverrideEntity` | `settings.adapters.relational.entity.RuntimeSettingOverrideEntity` |
| `infrastructure.persistence.relational.repository.JpaAiProfileRepository` | `ai.profiles.adapters.relational.repository.JpaAiProfileRepository` |
| `infrastructure.persistence.relational.repository.JpaKnowledgeBaseRepository` | `knowledgebase.adapters.relational.repository.JpaKnowledgeBaseRepository` |
| `infrastructure.persistence.relational.repository.JpaRuntimeSettingOverrideRepository` | `settings.adapters.relational.repository.JpaRuntimeSettingOverrideRepository` |
| `infrastructure.persistence.relational.repository.RelationalRepositoryMarker` | `bootstrap.persistence.RelationalRepositoryMarker` |
| `llm.AiModelOwnershipGuard` | `bootstrap.AiModelOwnershipGuard` |
| `llm.SpringAiLangChain4jChatModelAdapter` | `schemas.generation.adapters.model.SpringAiLangChain4jChatModelAdapter` |
| `observability.AdvancedSearchMetrics` | `search.runs.adapters.metrics.AdvancedSearchMetrics` |
| `repository.AiProfileRepository` | `ai.profiles.ports.AiProfileRepository` |
| `repository.GraphRepositoryMarker` | `bootstrap.persistence.GraphRepositoryMarker` |
| `repository.KnowledgeBaseRepository` | `knowledgebase.ports.KnowledgeBaseRepository` |
| `repository.LexicalIndexRepository` | `indexes.contracts.LexicalIndexRepository` |
| `repository.RuntimeSettingOverrideRepository` | `settings.ports.RuntimeSettingOverrideRepository` |
| `schemas.discovery.CandidateExtractionModelAdapter` | `schemas.discovery.adapters.model.CandidateExtractionModelAdapter` |
| `schemas.discovery.ModelResponseDiagnostics` | `schemas.discovery.adapters.model.ModelResponseDiagnostics` |
| `service.AiProfileContext` | `ai.execution.AiProfileContext` |
| `service.AiProfileService` | `ai.profiles.application.AiProfileService` |
| `service.AiRuntimeModelFactory` | `ai.adapters.provider.AiRuntimeModelFactory` |
| `service.EmbeddingSpace` | `ai.domain.EmbeddingSpace` |
| `service.EmbeddingSpaceIdentity` | `ai.domain.EmbeddingSpaceIdentity` |
| `service.EmbeddingSpaceIndexService` | `indexes.adapters.graph.EmbeddingSpaceIndexService` |
| `service.EmptyObjectProvider` | `ai.models.EmptyObjectProvider` |
| `service.KnowledgeBaseLifecycleService` | `knowledgebase.application.KnowledgeBaseLifecycleService` |
| `service.KnowledgeBaseService` | `knowledgebase.application.KnowledgeBaseService` |
| `service.LangChain4jSchemaGenerationService` | `schemas.generation.adapters.model.LangChain4jSchemaGenerationService` |
| `service.LexicalIndexIdentity` | `indexes.contracts.LexicalIndexIdentity` |
| `service.RuntimeSettingsService` | `settings.application.RuntimeSettingsService` |
| `service.SchemaBootstrapService` | `bootstrap.SchemaBootstrapService` |
| `service.SchemaGenerationNormalizationSupport` | `schemas.generation.domain.SchemaGenerationNormalizationSupport` |
| `service.SchemaGenerationService` | `schemas.generation.application.SchemaGenerationService` |

`bootstrap.AppProperties` is an assembly aggregate only. Its nested values bind
through owned `ModelProperties`, `Neo4jProperties`, `StorageProperties`,
`ChunkingProperties`, `QueryProperties`, and `ExtractionProperties`; features use
owned values, `SettingsStartupDefaults`, or `StartupModelMetadata` rather than the
aggregate. `AiProfileContext` exposes profile identity only; provider model capture
is hidden in `AiModelContext` and opaque `CapturedAiExecution`. Public index
contracts replace imports of index implementations.

### FROZEN_SCHEMA_BRIDGE_EDGES: 13 exact pairs

| Origin | Target | Final capability / permitted role | Permanent enforcing rule |
|---|---|---|---|
| `config.PersistenceConfiguration` | `schemas.registry.adapters.relational.entity.SchemaDefinitionEntity` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `config.PersistenceConfiguration` | `schemas.registry.adapters.relational.repository.JpaSchemaDefinitionRepository` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `controller.SchemaController` | `schemas.discovery.application.SchemaDiscoveryService` | schemas.discovery.application.SchemaDiscoveryService / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `controller.SchemaController` | `schemas.registry.application.SchemaRegistryService` | schemas.registry.application.SchemaRegistryService / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `controller.SchemaController` | `schemas.registry.domain.SchemaDefinitionNode` | schemas.registry.domain.SchemaDefinitionNode / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse` | `schemas.discovery.DiscoveryContracts$Candidate` | schemas.discovery.DiscoveryContracts$Candidate / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse` | `schemas.discovery.DiscoveryContracts$Conflict` | schemas.discovery.DiscoveryContracts$Conflict / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse` | `schemas.discovery.DiscoveryContracts$ResponseStatus` | schemas.discovery.DiscoveryContracts$ResponseStatus / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse$SourceOutcome` | `schemas.discovery.DiscoveryContracts$FailureCategory` | schemas.discovery.DiscoveryContracts$FailureCategory / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse$SourceOutcome` | `schemas.discovery.DiscoveryContracts$SourceStatus` | schemas.discovery.DiscoveryContracts$SourceStatus / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse$SourceOutcome` | `schemas.discovery.DiscoveryContracts$SourceType` | schemas.discovery.DiscoveryContracts$SourceType / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `dto.SchemaDiscoveryResponse$SourceOutcome` | `schemas.discovery.SourceFailureCode` | schemas.discovery.SourceFailureCode / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `service.SchemaBootstrapService` | `schemas.registry.application.SchemaRegistryService` | bootstrap assembly / concrete wiring | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |

### FROZEN_DOCUMENT_EDGES: 76 exact pairs

| Origin | Target | Final capability / permitted role | Permanent enforcing rule |
|---|---|---|---|
| `documents.application.inspection.DocumentSourceInputsFacade` | `error.NotFoundException` | error.NotFoundException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.inspection.DocumentEvaluationPreparationFacade` | `error.NotFoundException` | error.NotFoundException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentDryExtractionFacade` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `config.PersistenceConfiguration` | `documents.adapters.graph.repository.Neo4jDocumentChunkRepository` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `config.PersistenceConfiguration` | `documents.adapters.relational.entity.DocumentUploadEntity` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `config.PersistenceConfiguration` | `documents.adapters.relational.repository.JpaDocumentUploadRepository` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `documents.adapters.binary.DocumentBinaryStorageAdapter` | `storage.BinaryStorageService` | storage.BinaryStorageService / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.adapters.graph.DocumentChunkPersistenceAdapter` | `persistence.transaction.GraphTransactional` | persistence.transaction.GraphTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.adapters.graph.DocumentChunkPersistenceAdapter` | `repository.LexicalIndexRepository` | indexes.contracts.LexicalIndexRepository | only_index_owners_and_concrete_assembly_can_import_index_adapters |
| `documents.adapters.graph.DocumentChunkPersistenceAdapter` | `service.EmbeddingSpaceIndexService` | indexes.contracts.VectorIndexes | only_index_owners_and_concrete_assembly_can_import_index_adapters |
| `documents.adapters.graph.GraphArtifactCleanupService` | `repository.LexicalIndexRepository` | indexes.contracts.LexicalIndexRepository | only_index_owners_and_concrete_assembly_can_import_index_adapters |
| `documents.adapters.model.SpringAiGraphExtractionClient` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.adapters.model.SpringAiGraphExtractionClient` | `observability.AiModelCallObservation` | observability.AiModelCallObservation / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.adapters.model.SpringAiGraphExtractionClient` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.adapters.model.SpringAiGraphExtractionClient` | `observability.AiTokenUsage` | observability.AiTokenUsage / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.adapters.model.SpringAiGraphExtractionClient` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.adapters.model.SpringAiGraphExtractionClient` | `service.AiRuntimeModelFactory` | ai.models.AiModelAccess/AiModelPreparation/AiModelCache as required by caller role | features_use_public_foreign_capabilities_and_values / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `documents.adapters.relational.RelationalDocumentProcessingRunRepository` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.adapters.relational.RelationalDocumentStorageMutationRepository` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.api.model.DocumentChunkHierarchyResponse` | `dto.PageResponse` | http.contracts.PageResponse / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.api.model.DocumentChunkPageResponse` | `dto.PageResponse` | http.contracts.PageResponse / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.management.ChunkingStateService` | `config.AppProperties` | owned bound properties: Neo4jProperties for graph adapters, non-secret ModelProperties facts through StartupModelMetadata/SettingsStartupDefaults elsewhere | features_and_support_never_import_bootstrap_implementations / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `documents.application.management.ChunkingStateService` | `config.AppProperties$Model` | owned bound properties: Neo4jProperties for graph adapters, non-secret ModelProperties facts through StartupModelMetadata/SettingsStartupDefaults elsewhere | features_and_support_never_import_bootstrap_implementations / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `documents.application.management.ChunkingStateService` | `dto.RuntimeSettingResponse` | settings.api.model.RuntimeSettingResponse / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.management.ChunkingStateService` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.management.ChunkingStateService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `documents.application.management.ChunkingStateService` | `service.RuntimeSettingsService$ChunkingSettings` | settings.contracts.RuntimeSettingsAccess.ChunkingSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.management.DocumentStorageMutationService` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.management.DocumentUploadService` | `error.ConflictException` | error.ConflictException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.management.DocumentUploadService` | `error.NotFoundException` | error.NotFoundException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.management.DocumentUploadService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.management.DocumentUploadService` | `service.KnowledgeBaseLifecycleService` | ai.contracts.AiProfileAccess or knowledgebase.contracts public admission/profile capabilities | features_use_public_foreign_capabilities_and_values |
| `documents.application.processing.ChunkingService` | `domain.AiProfileNode` | ai.contracts.ProfileFacts through AiProfileAccess/KnowledgeBaseProfiles | features_use_public_foreign_capabilities_and_values / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.ChunkingService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `documents.application.processing.ChunkingService` | `service.RuntimeSettingsService$ChunkingSettings` | settings.contracts.RuntimeSettingsAccess.ChunkingSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.DocumentMigrationPreparationFacade` | `domain.AiProfileNode` | ai.contracts.ProfileFacts through AiProfileAccess/KnowledgeBaseProfiles | features_use_public_foreign_capabilities_and_values / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.DocumentMigrationPreparationFacade` | `error.EmbeddingSpaceConflictException` | ai.api.error.EmbeddingSpaceConflictException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentMigrationPreparationFacade` | `error.NotFoundException` | error.NotFoundException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingOptionsRegistry` | `error.ProcessingOptionsValidationException` | documents.api.error.ProcessingOptionsValidationException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `domain.AiProfileNode` | ai.contracts.ProfileFacts through AiProfileAccess/KnowledgeBaseProfiles | features_use_public_foreign_capabilities_and_values / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.DocumentProcessingService` | `error.ConflictException` | error.ConflictException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `error.NotFoundException` | error.NotFoundException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `observability.AiObservationScope` | observability.AiObservationScope / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `observability.AiWorkflowContext` | observability.AiWorkflowContext / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.DocumentProcessingService` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.processing.DocumentProcessingService` | `service.KnowledgeBaseLifecycleService` | ai.contracts.AiProfileAccess or knowledgebase.contracts public admission/profile capabilities | features_use_public_foreign_capabilities_and_values |
| `documents.application.processing.DocumentProcessingService` | `service.KnowledgeBaseService` | ai.contracts.AiProfileAccess or knowledgebase.contracts public admission/profile capabilities | features_use_public_foreign_capabilities_and_values |
| `documents.application.processing.DocumentReprocessingFacade` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.processing.DocumentReprocessingFacade` | `service.KnowledgeBaseService` | ai.contracts.AiProfileAccess or knowledgebase.contracts public admission/profile capabilities | features_use_public_foreign_capabilities_and_values |
| `documents.application.processing.DocumentRunHistoryLifecycle` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.EmbeddingPersistenceStage` | `domain.AiProfileNode` | ai.contracts.ProfileFacts through AiProfileAccess/KnowledgeBaseProfiles | features_use_public_foreign_capabilities_and_values / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.EmbeddingPersistenceStage` | `embedding.EmbeddingClient` | ai.models.EmbeddingClient / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.processing.EmbeddingPersistenceStage` | `infrastructure.ai.ProfileScopedAiClientResolver` | ai.models.ProfileScopedAiClientResolver / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.processing.EmbeddingPersistenceStage` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.processing.ExtractionRunLifecycle` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionService` | `observability.AiObservationScope` | observability.AiObservationScope / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionService` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionService` | `observability.AiWorkflowContext` | observability.AiWorkflowContext / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionStage` | `domain.AiProfileNode` | ai.contracts.ProfileFacts through AiProfileAccess/KnowledgeBaseProfiles | features_use_public_foreign_capabilities_and_values / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.GraphExtractionStage` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `documents.application.processing.GraphExtractionValidationService` | `error.GraphExtractionValidationException` | documents.api.error.GraphExtractionValidationException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionValidationService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `documents.application.processing.GraphExtractionValidationService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `documents.application.processing.GraphExtractionValidationService` | `service.RuntimeSettingsService$ExtractionSettings` | settings.contracts.RuntimeSettingsAccess.ExtractionSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `documents.application.processing.ProcessingRunLifecycle` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `domain.AiProfileNode` | `documents.domain.chunking.TokenizerId` | ai.domain.TokenizerId | features_use_public_foreign_capabilities_and_values / deterministic_values_cannot_resolve_live_settings_or_import_effects |
| `infrastructure.persistence.relational.RelationalAiProfileRepository` | `documents.domain.chunking.TokenizerId` | ai.domain.TokenizerId | features_use_public_foreign_capabilities_and_values / deterministic_values_cannot_resolve_live_settings_or_import_effects |
| `service.AiProfileService` | `documents.domain.chunking.TokenizerId` | ai.domain.TokenizerId | features_use_public_foreign_capabilities_and_values / deterministic_values_cannot_resolve_live_settings_or_import_effects |
| `service.EmbeddingSpaceIdentity` | `documents.domain.chunking.TokenizerId` | ai.domain.TokenizerId | features_use_public_foreign_capabilities_and_values / deterministic_values_cannot_resolve_live_settings_or_import_effects |
| `service.KnowledgeBaseService` | `documents.domain.chunking.TokenizerId` | ai.domain.TokenizerId | features_use_public_foreign_capabilities_and_values / deterministic_values_cannot_resolve_live_settings_or_import_effects |
| `service.RuntimeSettingsService` | `documents.domain.chunking.ChunkRevisionCalculator` | settings.ports.ChunkRevisionInspection -> bootstrap mapping -> documents.contracts.DocumentChunkRevisions, or search-local QueryPolicy composition | settings_does_not_interpret_feature_policy_or_chunking / mapping_adapters_have_only_public_values_and_consumer_ports |
| `service.RuntimeSettingsService` | `documents.domain.chunking.ChunkerRevision` | settings.ports.ChunkRevisionInspection -> bootstrap mapping -> documents.contracts.DocumentChunkRevisions, or search-local QueryPolicy composition | settings_does_not_interpret_feature_policy_or_chunking / mapping_adapters_have_only_public_values_and_consumer_ports |
| `service.RuntimeSettingsService` | `documents.domain.chunking.FixedCharacterChunkingStrategy` | settings.ports.ChunkRevisionInspection -> bootstrap mapping -> documents.contracts.DocumentChunkRevisions, or search-local QueryPolicy composition | settings_does_not_interpret_feature_policy_or_chunking / mapping_adapters_have_only_public_values_and_consumer_ports |

### FROZEN_DRAFT_LATER_EDGES: 2 exact pairs

| Origin | Target | Final capability / permitted role | Permanent enforcing rule |
|---|---|---|---|
| `config.PersistenceConfiguration` | `schemas.drafts.adapters.relational.entity.SchemaDraftEntity` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `config.PersistenceConfiguration` | `schemas.drafts.adapters.relational.repository.JpaSchemaDraftRepository` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |

### search outward: 74 exact pairs

| Origin | Target | Final capability / permitted role | Permanent enforcing rule |
|---|---|---|---|
| `search.answering.adapters.model.AdvancedSearchAnswerSynthesizer` | `infrastructure.ai.ProfileScopedAiClientResolver` | ai.models.ProfileScopedAiClientResolver / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.answering.adapters.model.AdvancedSearchAnswerSynthesizer` | `observability.AiModelCallObservation` | observability.AiModelCallObservation / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.answering.adapters.model.AdvancedSearchAnswerSynthesizer` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.answering.adapters.model.AdvancedSearchAnswerSynthesizer` | `observability.AiTokenUsage` | observability.AiTokenUsage / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.answering.adapters.model.AdvancedSearchSufficiencyEvaluator` | `infrastructure.ai.ProfileScopedAiClientResolver` | ai.models.ProfileScopedAiClientResolver / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.answering.adapters.model.AdvancedSearchSufficiencyEvaluator` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.answering.domain.AdvancedSearchFollowUpPolicy` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.query.adapters.graph.QueryNeo4jExecutor` | `config.AppProperties` | owned bound properties: Neo4jProperties for graph adapters, non-secret ModelProperties facts through StartupModelMetadata/SettingsStartupDefaults elsewhere | features_and_support_never_import_bootstrap_implementations / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `search.query.adapters.graph.QueryNeo4jExecutor` | `config.AppProperties$Neo4j` | owned bound properties: Neo4jProperties for graph adapters, non-secret ModelProperties facts through StartupModelMetadata/SettingsStartupDefaults elsewhere | features_and_support_never_import_bootstrap_implementations / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `search.query.adapters.model.SpringAiCypherGenerationClient` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.adapters.model.SpringAiCypherGenerationClient` | `observability.AiModelCallObservation` | observability.AiModelCallObservation / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.adapters.model.SpringAiCypherGenerationClient` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.adapters.model.SpringAiCypherGenerationClient` | `observability.AiTokenUsage` | observability.AiTokenUsage / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.adapters.model.SpringAiCypherGenerationClient` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.query.adapters.model.SpringAiCypherGenerationClient` | `service.AiRuntimeModelFactory` | ai.models.AiModelAccess/AiModelPreparation/AiModelCache as required by caller role | features_use_public_foreign_capabilities_and_values / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `search.query.api.QueryController` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherExecutionService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherExecutionService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.query.application.CypherGenerationService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherGenerationService` | `observability.AiObservationScope` | observability.AiObservationScope / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherGenerationService` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherGenerationService` | `observability.AiWorkflowContext` | observability.AiWorkflowContext / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherGenerationService` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.query.application.CypherGenerationService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.query.application.CypherValidationService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.CypherValidationService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.query.application.QueryAskService` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.QueryAskService` | `observability.AiObservationScope` | observability.AiObservationScope / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.QueryAskService` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.QueryAskService` | `observability.AiWorkflowContext` | observability.AiWorkflowContext / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.query.application.QueryAskService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.ranking.adapters.model.AdvancedSearchReranker` | `infrastructure.ai.ProfileScopedAiClientResolver` | ai.models.ProfileScopedAiClientResolver / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.ranking.application.AdvancedSearchRankingPipeline` | `observability.AiObservationScope` | observability.AiObservationScope / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.ranking.application.AdvancedSearchRankingPipeline` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.ranking.application.AdvancedSearchRankingPipeline` | `observability.AiWorkflowContext` | observability.AiWorkflowContext / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.retrieval.adapters.graph.Neo4jGraphRetrievalRepository` | `config.AppProperties` | owned bound properties: Neo4jProperties for graph adapters, non-secret ModelProperties facts through StartupModelMetadata/SettingsStartupDefaults elsewhere | features_and_support_never_import_bootstrap_implementations / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `search.retrieval.adapters.graph.Neo4jGraphRetrievalRepository` | `config.AppProperties$Neo4j` | owned bound properties: Neo4jProperties for graph adapters, non-secret ModelProperties facts through StartupModelMetadata/SettingsStartupDefaults elsewhere | features_and_support_never_import_bootstrap_implementations / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `search.retrieval.adapters.model.AdvancedSearchPlanner` | `infrastructure.ai.ProfileScopedAiClientResolver` | ai.models.ProfileScopedAiClientResolver / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.retrieval.adapters.model.AdvancedSearchPlanner` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.retrieval.adapters.model.DenseEmbeddingAdapter` | `embedding.EmbeddingClient` | ai.models.EmbeddingClient / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.retrieval.adapters.model.DenseEmbeddingAdapter` | `infrastructure.ai.ProfileScopedAiClientResolver` | ai.models.ProfileScopedAiClientResolver / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.retrieval.adapters.model.DenseEmbeddingAdapter` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.retrieval.application.AdvancedSearchGraphRetriever` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.retrieval.application.AdvancedSearchPlanValidator` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.retrieval.application.DenseTextRetriever` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.retrieval.application.DenseTextRetriever` | `service.EmbeddingSpaceIndexService` | indexes.contracts.VectorIndexes | only_index_owners_and_concrete_assembly_can_import_index_adapters |
| `search.retrieval.application.DocumentMetadataTextRetriever` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.retrieval.application.LexicalTextRetriever` | `logging.LogMetadata` | logging.LogMetadata / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.retrieval.application.LexicalTextRetriever` | `repository.LexicalIndexRepository` | indexes.contracts.LexicalIndexRepository | only_index_owners_and_concrete_assembly_can_import_index_adapters |
| `search.retrieval.application.validation.GraphPlanValidationService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.retrieval.application.validation.GraphPlanValidationService` | `service.RuntimeSettingsService$QuerySettings` | settings.contracts.RuntimeSettingsAccess.QuerySettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.runs.adapters.model.SearchProfileAdapter` | `domain.AiProfileNode` | ai.contracts.ProfileFacts through AiProfileAccess/KnowledgeBaseProfiles | features_use_public_foreign_capabilities_and_values / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.runs.adapters.model.SearchProfileAdapter` | `service.AiProfileService` | ai.contracts.AiProfileAccess or knowledgebase.contracts public admission/profile capabilities | features_use_public_foreign_capabilities_and_values |
| `search.runs.adapters.model.SearchProfileAdapter` | `service.AiRuntimeModelFactory` | ai.models.AiModelAccess/AiModelPreparation/AiModelCache as required by caller role | features_use_public_foreign_capabilities_and_values / provider_handles_are_confined_to_model_adapters_and_ai_resolution |
| `search.runs.adapters.model.SearchProfileAdapter` | `service.KnowledgeBaseService` | ai.contracts.AiProfileAccess or knowledgebase.contracts public admission/profile capabilities | features_use_public_foreign_capabilities_and_values |
| `search.runs.adapters.relational.repository.JpaAdvancedSearchRunRepository` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.api.AdvancedSearchRunController` | `dto.PageResponse` | http.contracts.PageResponse / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.api.error.AdvancedSearchResultUnavailableException` | `error.ConflictException` | error.ConflictException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.AdvancedSearchAdmission` | `config.AdvancedSearchProperties` | search.runs.configuration.AdvancedSearchProperties / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.runs.application.AdvancedSearchRunMaintenance` | `persistence.transaction.RelationalTransactional` | persistence.transaction.RelationalTransactional / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.AdvancedSearchRunMaintenance` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.runs.application.AdvancedSearchRunMaintenance` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.runs.application.AdvancedSearchRunService` | `dto.PageResponse` | http.contracts.PageResponse / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.AdvancedSearchRunService` | `error.NotFoundException` | error.NotFoundException / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.AdvancedSearchRunService` | `observability.AdvancedSearchMetrics` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `search.runs.application.AdvancedSearchRunService` | `service.RuntimeSettingsService` | settings.contracts.RuntimeSettingsAccess | foreign_consumers_cannot_import_settings_management_or_state |
| `search.runs.application.AdvancedSearchRunService` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.runs.application.DefaultAdvancedSearchRunProcessor` | `observability.AdvancedSearchMetrics` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `search.runs.application.DefaultAdvancedSearchRunProcessor` | `observability.AiObservationScope` | observability.AiObservationScope / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.DefaultAdvancedSearchRunProcessor` | `observability.AiObservationService` | observability.AiObservationService / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.DefaultAdvancedSearchRunProcessor` | `observability.AiWorkflowContext` | observability.AiWorkflowContext / governed support or owner-only access | support_cannot_depend_on_feature_interpretation_or_state / owned HTTP and exact transaction routing rules |
| `search.runs.application.DefaultAdvancedSearchRunProcessor` | `service.AiProfileContext` | ai.execution.AiProfileContext / governed support or owner-only access | features_use_public_foreign_capabilities_and_values / support_cannot_depend_on_feature_interpretation_or_state |
| `search.runs.application.DefaultAdvancedSearchRunProcessor` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |
| `search.runs.ports.AdvancedSearchRunProcessor$Context` | `service.RuntimeSettingsService$AdvancedSearchSettings` | settings.contracts.RuntimeSettingsAccess.AdvancedSearchSettings immutable snapshot | foreign_consumers_cannot_import_settings_management_or_state / public_fact_values_are_immutable_and_exclude_clients_state_and_credentials |

### search inward: 14 exact pairs

| Origin | Target | Final capability / permitted role | Permanent enforcing rule |
|---|---|---|---|
| `config.PersistenceConfiguration` | `search.runs.adapters.relational.entity.AdvancedSearchRunEntity` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `config.PersistenceConfiguration` | `search.runs.adapters.relational.repository.JpaAdvancedSearchRunRepository` | bootstrap assembly of owned implementation/configuration values | features_and_support_never_import_bootstrap_implementations / owned JPA and graph routing rules |
| `error.GlobalExceptionHandler` | `search.query.api.error.QueryDeadlineExceededException` | bootstrap.http adaptation of owner-specific API errors | features_and_support_never_import_bootstrap_implementations |
| `error.GlobalExceptionHandler` | `search.query.api.error.QueryRejectedException` | bootstrap.http adaptation of owner-specific API errors | features_and_support_never_import_bootstrap_implementations |
| `error.GlobalExceptionHandler` | `search.runs.api.error.AdvancedSearchCapacityException` | bootstrap.http adaptation of owner-specific API errors | features_and_support_never_import_bootstrap_implementations |
| `error.GlobalExceptionHandler` | `search.runs.api.error.AdvancedSearchReadinessConflictException` | bootstrap.http adaptation of owner-specific API errors | features_and_support_never_import_bootstrap_implementations |
| `error.GlobalExceptionHandler` | `search.runs.api.error.AdvancedSearchResultUnavailableException` | bootstrap.http adaptation of owner-specific API errors | features_and_support_never_import_bootstrap_implementations |
| `observability.AdvancedSearchMetrics` | `search.answering.domain.AdvancedSearchAnswerContracts$Answer` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `observability.AdvancedSearchMetrics` | `search.answering.domain.AdvancedSearchAnswerContracts$AnswerStatus` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `observability.AdvancedSearchMetrics` | `search.answering.domain.AdvancedSearchAnswerContracts$DiagnosticResult` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `observability.AdvancedSearchMetrics` | `search.answering.domain.AnswerSynthesis$Outcome` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `observability.AdvancedSearchMetrics` | `search.runs.domain.AdvancedSearchRunStatus` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `observability.AdvancedSearchMetrics` | `search.runs.ports.AdvancedSearchRunProcessor$Attempt` | search.runs.adapters.metrics.AdvancedSearchMetrics | generic_observation_support_has_no_search_interpretation |
| `service.RuntimeSettingsService` | `search.query.domain.QueryPolicy` | settings.ports.ChunkRevisionInspection -> bootstrap mapping -> documents.contracts.DocumentChunkRevisions, or search-local QueryPolicy composition | settings_does_not_interpret_feature_policy_or_chunking / mapping_adapters_have_only_public_values_and_consumer_ports |

### Identity and graph-client bridge baselines

| Exact baseline | Replacement rule |
|---|---|
| `EmbeddingSpaceIdentity` callers: itself, `EmbeddingSpaceIndexService`, `LexicalIndexIdentity` | AI deterministic identity values consumed by index contracts/adapters; reject workflow/state access |
| `EmbeddingSpace` callers: itself, `EmbeddingSpaceIdentity`, `EmbeddingSpaceIndexService` | AI immutable index identity; no provider/state dependency |
| Graph client exceptions: `PersistenceConfiguration`, `EmbeddingSpaceIndexService` | Bootstrap persistence wiring and index graph adapter roles; reject graph clients elsewhere |

Transaction self-invocation allowances are independent and audited in the compatibility checklist; none authorizes a cross-owner dependency.

### Final accounting

- 76 document + 13 schema + 2 draft + 74 outward search + 14 inward search =
  **179 baseline pairs**, each retained above as historical evidence only.
- All frozen constants, search resource inventories, identity frozen-caller tests,
  and relocated legacy graph-client exceptions are removed. Shared graph effects
  are confined to owned graph adapters and concrete bootstrap wiring.
- `FinalSupportBoundaryTest` rejects foreign mutable management/state, reverse
  feature dependencies, provider handles outside model roles, effect dependencies
  (including store-qualified annotations) in domains, support interpretation of
  feature state, concrete index adapters from any foreign feature/support/mapping,
  and feature imports of bootstrap. Negative fixtures exercise forbidden neighbors
  and allowed immutable snapshots/profile facts/index contracts.
- `ArchitectureBoundaryTest` preserves predecessor guards and exact independently
  audited transaction self-call signatures, rejecting stale and new signatures.
  There is no whole-class or whole-package transaction allowance.
- Historical identities and verification evidence are recorded in
  [compatibility-audit.md](compatibility-audit.md). No SQL or binary migration is
  introduced by the ownership moves.
