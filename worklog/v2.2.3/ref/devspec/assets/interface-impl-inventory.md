# 接口 / 抽象类 / 实现关系清单

- 服务根：['/opt/prod/s10/v2.2.3/source/dts-platform/src/main/java', '/opt/prod/s10/v2.2.3/source/dts-ingestion/src/main/java', '/opt/prod/s10/v2.2.3/source/dts-admin/src/main/java', '/opt/prod/s10/v2.2.3/source/dts-analytics/src/main/java', '/opt/prod/s10/v2.2.3/source/dts-common/src/main/java']

## dts-platform

- 接口数：198；多实现接口：3；单实现接口：66
- 出现父类/被继承的类型：11

### 多实现接口

| 接口 | 实现类 | 定位 |
|---|---|---|
| `CatalogManagedCopyDestructionAdapter` | ExternalJdbcCopyDestructionAdapter<br>ManagedDbtCopyDestructionAdapter<br>ManagedFileCopyDestructionAdapter<br>ManagedPostgresCopyDestructionAdapter | `com/yuzhi/dts/platform/service/catalog/ExternalJdbcCopyDestructionAdapter.java`<br>`com/yuzhi/dts/platform/service/catalog/ManagedDbtCopyDestructionAdapter.java`<br>`com/yuzhi/dts/platform/service/catalog/ManagedFileCopyDestructionAdapter.java`<br>`com/yuzhi/dts/platform/service/catalog/ManagedPostgresCopyDestructionAdapter.java` |
| `CatalogClassificationProjection` | CatalogConsumerDependencyProjection<br>CatalogDatasetClassificationProjection<br>CatalogGrantClassificationProjection | `com/yuzhi/dts/platform/service/catalog/CatalogConsumerDependencyProjection.java`<br>`com/yuzhi/dts/platform/service/catalog/CatalogDatasetClassificationProjection.java`<br>`com/yuzhi/dts/platform/service/catalog/CatalogGrantClassificationProjection.java` |
| `QueryGateway` | HiveQueryGateway<br>JdbcQueryGateway<br>NoopQueryGateway | `com/yuzhi/dts/platform/service/query/HiveQueryGateway.java`<br>`com/yuzhi/dts/platform/service/query/JdbcQueryGateway.java`<br>`com/yuzhi/dts/platform/service/query/NoopQueryGateway.java` |

### 抽象类 / 被继承类

| 父类型 | 子类 | 定位 |
|---|---|---|
| `AbstractAuditingEntity` | 共 123 个子类（JPA 实体公共基类，不逐条展开） | — |
| `CompositeConverter` | CRLFLogConverter | `com/yuzhi/dts/platform/config/CRLFLogConverter.java` |
| `ErrorResponseException` | BadRequestAlertException | `com/yuzhi/dts/platform/web/rest/errors/BadRequestAlertException.java` |
| `HiveConnectionTestRequest` | HiveConnectionPersistRequest | `com/yuzhi/dts/platform/service/infra/dto/HiveConnectionPersistRequest.java` |
| `IllegalArgumentException` | IndicatorRequestException<br>StandardPackageContractException | `com/yuzhi/dts/platform/service/governance/IndicatorRequestException.java`<br>`com/yuzhi/dts/platform/service/modeling/StandardPackageContractException.java` |
| `IllegalStateException` | CatalogTagMigrationConflictException<br>IndicatorConflictException | `com/yuzhi/dts/platform/service/catalog/CatalogTagMigrationConflictException.java`<br>`com/yuzhi/dts/platform/service/governance/IndicatorConflictException.java` |
| `OncePerRequestFilter` | ServiceDependencyAuthenticationFilter<br>ModelingIdentityFilter<br>PortalSessionInactivityFilter<br>AuditLoggingFilter<br>DbtImplementationDraftBodyLimitFilter<br>DimensionModelBodyLimitFilter<br>ModelArchiveUploadLimitFilter<br>ModelSpecImportCorrelationFilter<br>TestApiAuthFilter | `com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`<br>`com/yuzhi/dts/platform/security/modeling/ModelingIdentityFilter.java`<br>`com/yuzhi/dts/platform/security/session/PortalSessionInactivityFilter.java`<br>`com/yuzhi/dts/platform/web/filter/AuditLoggingFilter.java`<br>`com/yuzhi/dts/platform/web/filter/DbtImplementationDraftBodyLimitFilter.java`<br>`com/yuzhi/dts/platform/web/filter/DimensionModelBodyLimitFilter.java`<br>`com/yuzhi/dts/platform/web/filter/ModelArchiveUploadLimitFilter.java`<br>`com/yuzhi/dts/platform/web/filter/ModelSpecImportCorrelationFilter.java`<br>`com/yuzhi/dts/platform/web/filter/TestApiAuthFilter.java` |
| `ResponseEntityExceptionHandler` | ExceptionTranslator | `com/yuzhi/dts/platform/web/rest/errors/ExceptionTranslator.java` |
| `ResponseStatusException` | CatalogAssetTagPermissionException | `com/yuzhi/dts/platform/service/catalog/CatalogAssetTagPermissionException.java` |
| `RuntimeException` | 共 25 个子类（JPA 实体公共基类，不逐条展开） | — |
| `SpringBootServletInitializer` | ApplicationWebXml | `com/yuzhi/dts/platform/ApplicationWebXml.java` |

### 单实现接口（清单）

| 接口 | 实现 | 定位 |
|---|---|---|
| `AssetActionSubjectResolver` | `SecurityContextAssetActionSubjectResolver` | `com/yuzhi/dts/platform/service/security/SecurityContextAssetActionSubjectResolver.java` |
| `AsyncConfigurer` | `AsyncConfiguration` | `com/yuzhi/dts/platform/config/AsyncConfiguration.java` |
| `AuditorAware` | `SpringSecurityAuditorAware` | `com/yuzhi/dts/platform/security/SpringSecurityAuditorAware.java` |
| `BearerTokenResolver` | `PortalSessionBearerTokenResolver` | `com/yuzhi/dts/platform/security/session/PortalSessionBearerTokenResolver.java` |
| `CanonicalModelIdentityReadPort` | `JdbcCanonicalModelIdentityReadAdapter` | `com/yuzhi/dts/platform/service/catalog/JdbcCanonicalModelIdentityReadAdapter.java` |
| `CatalogAssetAvailabilityReadPort` | `JdbcCatalogAssetAvailabilityReadAdapter` | `com/yuzhi/dts/platform/service/catalog/JdbcCatalogAssetAvailabilityReadAdapter.java` |
| `CatalogAssetQualityStatusReader` | `JdbcCatalogAssetQualityStatusReader` | `com/yuzhi/dts/platform/repository/catalog/JdbcCatalogAssetQualityStatusReader.java` |
| `CatalogAssetSemanticStore` | `JdbcCatalogAssetSemanticStore` | `com/yuzhi/dts/platform/repository/catalog/JdbcCatalogAssetSemanticStore.java` |
| `CatalogClassificationBoundary` | `CatalogClassificationBoundaryAdapter` | `com/yuzhi/dts/platform/service/catalog/CatalogClassificationBoundaryAdapter.java` |
| `CatalogDomainAccessReadPort` | `JpaCatalogDomainAccessReadAdapter` | `com/yuzhi/dts/platform/service/catalog/JpaCatalogDomainAccessReadAdapter.java` |
| `CatalogDomainActorProvider` | `SecurityCatalogDomainActorProvider` | `com/yuzhi/dts/platform/service/catalog/SecurityCatalogDomainActorProvider.java` |
| `CatalogDomainDictionaryReadPort` | `CatalogDomainDictionaryReadAdapter` | `com/yuzhi/dts/platform/service/catalog/CatalogDomainDictionaryReadAdapter.java` |
| `CatalogDomainResolutionPort` | `CatalogDomainResolutionAdapter` | `com/yuzhi/dts/platform/service/modeling/warehouse/CatalogDomainResolutionAdapter.java` |
| `CatalogMaterializationSourceAvailabilityPort` | `JdbcCatalogMaterializationSourceAvailabilityAdapter` | `com/yuzhi/dts/platform/service/catalog/JdbcCatalogMaterializationSourceAvailabilityAdapter.java` |
| `CatalogPublicationPolicyPort` | `CatalogPublicationPolicyAdapter` | `com/yuzhi/dts/platform/service/catalog/CatalogPublicationPolicyAdapter.java` |
| `CatalogSourceReferenceReadPort` | `JpaCatalogSourceReferenceReadAdapter` | `com/yuzhi/dts/platform/service/catalog/JpaCatalogSourceReferenceReadAdapter.java` |
| `CatalogTagMigrationStore` | `JdbcCatalogTagMigrationStore` | `com/yuzhi/dts/platform/service/catalog/JdbcCatalogTagMigrationStore.java` |
| `DbtExecutionGateway` | `AirflowDbtExecutionGateway` | `com/yuzhi/dts/platform/service/etl/AirflowDbtExecutionGateway.java` |
| `GovernanceIndicatorEvidenceReadPort` | `GovernanceIndicatorEvidenceReadService` | `com/yuzhi/dts/platform/service/governance/GovernanceIndicatorEvidenceReadService.java` |
| `GovernanceQualityRerunPort` | `GovernanceQualityRerunAdapter` | `com/yuzhi/dts/platform/service/governance/GovernanceQualityRerunAdapter.java` |
| `GovernanceReferenceAssetReadPort` | `GovernanceReferenceAssetReadService` | `com/yuzhi/dts/platform/service/governance/GovernanceReferenceAssetReadService.java` |
| `GovernanceReferenceCodePackagePort` | `GovernanceReferenceCodePackageService` | `com/yuzhi/dts/platform/service/governance/GovernanceReferenceCodePackageService.java` |
| `GovernanceStandardEvidenceReadPort` | `GovernanceStandardEvidenceReadService` | `com/yuzhi/dts/platform/service/governance/GovernanceStandardEvidenceReadService.java` |
| `GovernedStandardReadPort` | `JpaGovernedStandardReadAdapter` | `com/yuzhi/dts/platform/service/modeling/JpaGovernedStandardReadAdapter.java` |
| `IndicatorBusinessContextReadPort` | `JdbcIndicatorBusinessContextReadAdapter` | `com/yuzhi/dts/platform/repository/governance/JdbcIndicatorBusinessContextReadAdapter.java` |
| `IndicatorSuggestionProvider` | `NoOpIndicatorSuggestionProvider` | `com/yuzhi/dts/platform/service/governance/spi/NoOpIndicatorSuggestionProvider.java` |
| `InitializingBean` | `PlatformInboundServiceAuthProperties` | `com/yuzhi/dts/platform/config/PlatformInboundServiceAuthProperties.java` |
| `LegacyCodeSetMigrationPort` | `JpaLegacyCodeSetMigrationAdapter` | `com/yuzhi/dts/platform/service/modeling/JpaLegacyCodeSetMigrationAdapter.java` |
| `ModelGovernancePolicyPort` | `JdbcModelGovernancePolicyAdapter` | `com/yuzhi/dts/platform/service/modeling/JdbcModelGovernancePolicyAdapter.java` |
| `ModelImplementationDependencyReadPort` | `ModelImplementationDependencyReadAdapter` | `com/yuzhi/dts/platform/repository/modeling/ModelImplementationDependencyReadAdapter.java` |
| `ModelLifecycleCompilerPort` | `CanonicalModelLifecycleCompilerAdapter` | `com/yuzhi/dts/platform/service/modeling/CanonicalModelLifecycleCompilerAdapter.java` |
| `ModelLifecycleTestEvidencePort` | `DbtRunLifecycleTestEvidenceAdapter` | `com/yuzhi/dts/platform/service/modeling/DbtRunLifecycleTestEvidenceAdapter.java` |
| `ModelMaterializationPlanRelationPort` | `ModelMaterializationPlanRelationReadAdapter` | `com/yuzhi/dts/platform/repository/modeling/ModelMaterializationPlanRelationReadAdapter.java` |
| `ModelPhysicalPreviewReferencePort` | `CatalogModelPhysicalPreviewReferenceAdapter` | `com/yuzhi/dts/platform/service/modeling/representation/CatalogModelPhysicalPreviewReferenceAdapter.java` |
| `ModelReleaseCandidateRetryDriftGate` | `ModelMaterializationBuildRepository` | `com/yuzhi/dts/platform/repository/modeling/ModelMaterializationBuildRepository.java` |
| `ModelRepresentationEvidencePort` | `CanonicalModelRepresentationEvidenceAdapter` | `com/yuzhi/dts/platform/service/modeling/representation/CanonicalModelRepresentationEvidenceAdapter.java` |
| `ModelSpecDomainReadAccessPort` | `ModelSpecDomainReadAccessAdapter` | `com/yuzhi/dts/platform/service/modeling/ModelSpecDomainReadAccessAdapter.java` |
| `ModelSpecDomainWriteAccessPort` | `ModelSpecDomainWriteAccessAdapter` | `com/yuzhi/dts/platform/service/modeling/ModelSpecDomainWriteAccessAdapter.java` |
| `ModelSpecImportPreviewCommitPort` | `ModelSpecImportPreviewCommitService` | `com/yuzhi/dts/platform/service/modeling/imports/preview/ModelSpecImportPreviewCommitService.java` |
| `ModelSpecPlanWriteAccessPort` | `ModelSpecPlanWriteAccessAdapter` | `com/yuzhi/dts/platform/service/modeling/ModelSpecPlanWriteAccessAdapter.java` |
| `ModelSpecSourceValidationPort` | `ModelSpecSourceValidationAdapter` | `com/yuzhi/dts/platform/service/modeling/ModelSpecSourceValidationAdapter.java` |
| `ModelSpecStandardEvidencePort` | `GovernanceModelSpecStandardEvidenceAdapter` | `com/yuzhi/dts/platform/service/modeling/GovernanceModelSpecStandardEvidenceAdapter.java` |
| `ModelSpecWriteAccessPort` | `ModelSpecAccessService` | `com/yuzhi/dts/platform/service/modeling/ModelSpecAccessService.java` |
| `OAuth2TokenValidator` | `AudienceValidator` | `com/yuzhi/dts/platform/security/oauth2/AudienceValidator.java` |
| `OpaqueTokenIntrospector` | `PortalOpaqueTokenIntrospector` | `com/yuzhi/dts/platform/security/session/PortalOpaqueTokenIntrospector.java` |
| `PhysicalPreviewAccessPort` | `DefaultPhysicalPreviewAccessAdapter` | `com/yuzhi/dts/platform/service/modeling/serving/DefaultPhysicalPreviewAccessAdapter.java` |
| `PhysicalPreviewPolicyPort` | `DefaultPhysicalPreviewPolicyAdapter` | `com/yuzhi/dts/platform/service/modeling/serving/DefaultPhysicalPreviewPolicyAdapter.java` |
| `PhysicalPreviewQueryPort` | `PostgresPhysicalPreviewQueryAdapter` | `com/yuzhi/dts/platform/service/modeling/serving/PostgresPhysicalPreviewQueryAdapter.java` |
| `PhysicalRelationInspector` | `PostgresPhysicalRelationInspector` | `com/yuzhi/dts/platform/service/modeling/PostgresPhysicalRelationInspector.java` |
| `PolicyDecisionService` | `NoopPolicyDecisionService` | `com/yuzhi/dts/platform/service/policy/NoopPolicyDecisionService.java` |
| `QualityEvidencePort` | `JdbcGovernanceQualityEvidenceAdapter` | `com/yuzhi/dts/platform/service/governance/JdbcGovernanceQualityEvidenceAdapter.java` |
| `QualityStatementExecutor` | `PgStatementExecutor` | `com/yuzhi/dts/platform/service/governance/PgStatementExecutor.java` |
| `ReleaseCandidateWorkbenchEvidencePort` | `ReleaseCandidateWorkbenchEvidenceRepository` | `com/yuzhi/dts/platform/repository/modeling/ReleaseCandidateWorkbenchEvidenceRepository.java` |
| `ReleaseDutyDirectoryPort` | `AdminReleaseDutyDirectoryAdapter` | `com/yuzhi/dts/platform/service/admin/gateway/directory/AdminReleaseDutyDirectoryAdapter.java` |
| `SchemaDriftConsumerReferenceReadPort` | `ModelSpecSchemaDriftConsumerReferenceReadAdapter` | `com/yuzhi/dts/platform/repository/modeling/ModelSpecSchemaDriftConsumerReferenceReadAdapter.java` |
| `ServiceAssetIdentityReadPort` | `JpaServiceAssetIdentityReadAdapter` | `com/yuzhi/dts/platform/service/services/JpaServiceAssetIdentityReadAdapter.java` |
| `ServletContextInitializer` | `WebConfigurer` | `com/yuzhi/dts/platform/config/WebConfigurer.java` |
| `SourceReferenceResolver` | `SourceReferenceResolverAdapter` | `com/yuzhi/dts/platform/service/modeling/warehouse/SourceReferenceResolverAdapter.java` |
| `SqlIdeTabService` | `SqlIdeTabServiceImpl` | `com/yuzhi/dts/platform/service/sql/SqlIdeTabServiceImpl.java` |
| `SqlPlanService` | `SqlPlanServiceImpl` | `com/yuzhi/dts/platform/service/sql/SqlPlanServiceImpl.java` |
| `SqlResultStreamService` | `SqlResultStreamServiceImpl` | `com/yuzhi/dts/platform/service/sql/SqlResultStreamServiceImpl.java` |
| `SqlSubqueryService` | `SqlSubqueryServiceImpl` | `com/yuzhi/dts/platform/service/sql/SqlSubqueryServiceImpl.java` |
| `StandardPackageInstallLedgerPort` | `StandardPackageInstallLedgerService` | `com/yuzhi/dts/platform/service/modeling/StandardPackageInstallLedgerService.java` |
| `WarehousePlanDownstreamEvidencePort` | `WarehousePlanDownstreamEvidenceAdapter` | `com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanDownstreamEvidenceAdapter.java` |
| `WarehousePlanOperationsReadPort` | `WarehousePlanOperationsReadAdapter` | `com/yuzhi/dts/platform/service/modeling/warehouse/WarehousePlanOperationsReadAdapter.java` |
| `WebMvcConfigurer` | `DateTimeFormatConfiguration` | `com/yuzhi/dts/platform/config/DateTimeFormatConfiguration.java` |

## dts-ingestion

- 接口数：18；多实现接口：0；单实现接口：5
- 出现父类/被继承的类型：3

### 多实现接口

| 接口 | 实现类 | 定位 |
|---|---|---|

### 抽象类 / 被继承类

| 父类型 | 子类 | 定位 |
|---|---|---|
| `AbstractAuditingEntity` | IngestionSchemaSnapshot<br>IngestionTask<br>IngestionTaskChangeLog | `com/yuzhi/dts/ingestion/domain/IngestionSchemaSnapshot.java`<br>`com/yuzhi/dts/ingestion/domain/IngestionTask.java`<br>`com/yuzhi/dts/ingestion/domain/IngestionTaskChangeLog.java` |
| `OncePerRequestFilter` | ServiceDependencyAuthenticationFilter | `com/yuzhi/dts/ingestion/security/ServiceDependencyAuthenticationFilter.java` |
| `RuntimeException` | ApiHttpException | `com/yuzhi/dts/ingestion/service/etl/api/ApiHttpException.java` |

### 单实现接口（清单）

| 接口 | 实现 | 定位 |
|---|---|---|
| `AuditorAware` | `SpringSecurityAuditorAware` | `com/yuzhi/dts/ingestion/security/SpringSecurityAuditorAware.java` |
| `ConfirmationPolicy` | `ModalConfirmationPolicy` | `com/yuzhi/dts/ingestion/service/etl/rollback/ModalConfirmationPolicy.java` |
| `HealthIndicator` | `IngestionTaskSecretMigrationService` | `com/yuzhi/dts/ingestion/service/IngestionTaskSecretMigrationService.java` |
| `ResponseBodyAdvice` | `IngestionJsonResponseSanitizerAdvice` | `com/yuzhi/dts/ingestion/web/rest/IngestionJsonResponseSanitizerAdvice.java` |
| `SourceConnector` | `ApiHttpSourceConnector` | `com/yuzhi/dts/ingestion/service/etl/api/ApiHttpSourceConnector.java` |

## dts-admin

- 接口数：26；多实现接口：1；单实现接口：7
- 出现父类/被继承的类型：8

### 多实现接口

| 接口 | 实现类 | 定位 |
|---|---|---|
| `KeycloakAdminClient` | InMemoryKeycloakAdminClient<br>KeycloakAdminRestClient | `com/yuzhi/dts/admin/service/keycloak/InMemoryKeycloakAdminClient.java`<br>`com/yuzhi/dts/admin/service/keycloak/KeycloakAdminRestClient.java` |

### 抽象类 / 被继承类

| 父类型 | 子类 | 定位 |
|---|---|---|
| `AbstractAuditingEntity` | AdminApprovalRequest<br>AdminCustomRole<br>AdminDataset<br>AdminKeycloakUser<br>AdminRoleAssignment<br>AdminRoleMember<br>AdminWorkflowStep<br>AdminWorkflowTemplate<br>ChangeRequest<br>InfraDataSource<br>OrganizationNode<br>PersonImportBatch<br>PersonImportRecord<br>PersonProfile<br>PortalMenu<br>PortalMenuVisibility<br>SystemConfig | `com/yuzhi/dts/admin/domain/AdminApprovalRequest.java`<br>`com/yuzhi/dts/admin/domain/AdminCustomRole.java`<br>`com/yuzhi/dts/admin/domain/AdminDataset.java`<br>`com/yuzhi/dts/admin/domain/AdminKeycloakUser.java`<br>`com/yuzhi/dts/admin/domain/AdminRoleAssignment.java`<br>`com/yuzhi/dts/admin/domain/AdminRoleMember.java`<br>`com/yuzhi/dts/admin/domain/AdminWorkflowStep.java`<br>`com/yuzhi/dts/admin/domain/AdminWorkflowTemplate.java`<br>`com/yuzhi/dts/admin/domain/ChangeRequest.java`<br>`com/yuzhi/dts/admin/domain/InfraDataSource.java`<br>`com/yuzhi/dts/admin/domain/OrganizationNode.java`<br>`com/yuzhi/dts/admin/domain/PersonImportBatch.java`<br>`com/yuzhi/dts/admin/domain/PersonImportRecord.java`<br>`com/yuzhi/dts/admin/domain/PersonProfile.java`<br>`com/yuzhi/dts/admin/domain/PortalMenu.java`<br>`com/yuzhi/dts/admin/domain/PortalMenuVisibility.java`<br>`com/yuzhi/dts/admin/domain/SystemConfig.java` |
| `CompositeConverter` | CRLFLogConverter | `com/yuzhi/dts/admin/config/CRLFLogConverter.java` |
| `ErrorResponseException` | BadRequestAlertException | `com/yuzhi/dts/admin/web/rest/errors/BadRequestAlertException.java` |
| `HiveConnectionTestRequest` | HiveConnectionPersistRequest | `com/yuzhi/dts/admin/service/infra/dto/HiveConnectionPersistRequest.java` |
| `OncePerRequestFilter` | AuditIngestPreAuthenticationFilter<br>SessionInactivityFilter<br>TestApiAuthFilter | `com/yuzhi/dts/admin/web/filter/AuditIngestPreAuthenticationFilter.java`<br>`com/yuzhi/dts/admin/web/filter/SessionInactivityFilter.java`<br>`com/yuzhi/dts/admin/web/filter/TestApiAuthFilter.java` |
| `ResponseEntityExceptionHandler` | ExceptionTranslator | `com/yuzhi/dts/admin/web/rest/errors/ExceptionTranslator.java` |
| `RuntimeException` | PersonnelImportException | `com/yuzhi/dts/admin/service/personnel/PersonnelImportException.java` |
| `SpringBootServletInitializer` | ApplicationWebXml | `com/yuzhi/dts/admin/ApplicationWebXml.java` |

### 单实现接口（清单）

| 接口 | 实现 | 定位 |
|---|---|---|
| `AsyncConfigurer` | `AsyncConfiguration` | `com/yuzhi/dts/admin/config/AsyncConfiguration.java` |
| `AuditorAware` | `SpringSecurityAuditorAware` | `com/yuzhi/dts/admin/security/SpringSecurityAuditorAware.java` |
| `HealthIndicator` | `EurekaWorkaroundConfiguration` | `com/yuzhi/dts/admin/config/EurekaWorkaroundConfiguration.java` |
| `InitializingBean` | `AdminInboundServiceAuthProperties` | `com/yuzhi/dts/admin/config/AdminInboundServiceAuthProperties.java` |
| `OAuth2TokenValidator` | `AudienceValidator` | `com/yuzhi/dts/admin/security/oauth2/AudienceValidator.java` |
| `ServletContextInitializer` | `WebConfigurer` | `com/yuzhi/dts/admin/config/WebConfigurer.java` |
| `WebMvcConfigurer` | `DateTimeFormatConfiguration` | `com/yuzhi/dts/admin/config/DateTimeFormatConfiguration.java` |

## dts-analytics

- 接口数：48；多实现接口：0；单实现接口：7
- 出现父类/被继承的类型：3

### 多实现接口

| 接口 | 实现类 | 定位 |
|---|---|---|

### 抽象类 / 被继承类

| 父类型 | 子类 | 定位 |
|---|---|---|
| `OncePerRequestFilter` | AnalyticsAuditLoggingFilter<br>AnalyticsAuthenticationFilter<br>DtsRequestContextFilter<br>PlatformPermissionFilter<br>PlatformSessionBridgeFilter<br>RequestIdFilter<br>RequestLoggingFilter | `com/yuzhi/dts/analytics/web/filter/AnalyticsAuditLoggingFilter.java`<br>`com/yuzhi/dts/analytics/web/filter/AnalyticsAuthenticationFilter.java`<br>`com/yuzhi/dts/analytics/web/filter/DtsRequestContextFilter.java`<br>`com/yuzhi/dts/analytics/web/filter/PlatformPermissionFilter.java`<br>`com/yuzhi/dts/analytics/web/filter/PlatformSessionBridgeFilter.java`<br>`com/yuzhi/dts/analytics/web/filter/RequestIdFilter.java`<br>`com/yuzhi/dts/analytics/web/filter/RequestLoggingFilter.java` |
| `RuntimeException` | AnalysisConflictException<br>AnalysisDependencyException<br>AnalysisForbiddenException<br>AnalysisNotFoundException<br>AnalysisQueryCancelledException<br>AnalysisQueryTimeoutException<br>AnalysisRateLimitException<br>AnalysisSpecValidationException<br>ScreenSpecValidationException | `com/yuzhi/dts/analytics/service/analysis/AnalysisConflictException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisDependencyException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisForbiddenException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisNotFoundException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisQueryCancelledException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisQueryTimeoutException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisRateLimitException.java`<br>`com/yuzhi/dts/analytics/service/analysis/AnalysisSpecValidationException.java`<br>`com/yuzhi/dts/analytics/web/rest/errors/ScreenSpecValidationException.java` |
| `TextWebSocketHandler` | ScreenCollaborationWebSocketHandler | `com/yuzhi/dts/analytics/web/socket/ScreenCollaborationWebSocketHandler.java` |

### 单实现接口（清单）

| 接口 | 实现 | 定位 |
|---|---|---|
| `AccessDeniedHandler` | `SecurityProblemSupport` | `com/yuzhi/dts/analytics/config/SecurityProblemSupport.java` |
| `AuthenticationEntryPoint` | `SecurityProblemSupport` | `com/yuzhi/dts/analytics/config/SecurityProblemSupport.java` |
| `GovernedAnalysisDatasetContractProvider` | `PlatformAnalysisDatasetContractClient` | `com/yuzhi/dts/analytics/service/analysis/PlatformAnalysisDatasetContractClient.java` |
| `HandlerMethodArgumentResolver` | `RequestContextArgumentResolver` | `com/yuzhi/dts/analytics/web/support/RequestContextArgumentResolver.java` |
| `HandshakeInterceptor` | `ScreenCollaborationHandshakeInterceptor` | `com/yuzhi/dts/analytics/web/socket/ScreenCollaborationHandshakeInterceptor.java` |
| `HealthIndicator` | `ReportRegistrationHealthIndicator` | `com/yuzhi/dts/analytics/service/observability/ReportRegistrationHealthIndicator.java` |
| `WebSocketConfigurer` | `ScreenCollaborationWebSocketConfig` | `com/yuzhi/dts/analytics/config/ScreenCollaborationWebSocketConfig.java` |

## dts-common

- 接口数：1；多实现接口：0；单实现接口：0
- 出现父类/被继承的类型：0

### 多实现接口

| 接口 | 实现类 | 定位 |
|---|---|---|

### 抽象类 / 被继承类

| 父类型 | 子类 | 定位 |
|---|---|---|

### 单实现接口（清单）

| 接口 | 实现 | 定位 |
|---|---|---|
