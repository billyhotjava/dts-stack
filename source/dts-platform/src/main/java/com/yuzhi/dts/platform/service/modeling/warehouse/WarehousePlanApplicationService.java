package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftConsumerReferenceReadPort;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetailsReader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanResult;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.InitialSourceRef;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceChangeImpact;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceDiffSummary;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceSchemaChange;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WarehousePlanApplicationService {

    private static final String HEADER_COLUMNS = """
        select id, tenant_id, code, name, objective, scope, owner_id, owner_department_id,
               onboarding_mode, lifecycle_status, version
          from modeling_warehouse_plan
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CatalogDomainResolutionPort catalogDomainResolutionPort;
    private final SourceReferenceResolver sourceReferenceResolver;
    private final AuditService auditService;
    private final SchemaDriftConsumerReferenceReadPort driftConsumerReferences;
    private final SchemaDriftDetailsReader schemaDriftDetailsReader;
    private final TransactionOperations transactions;
    private final TransactionOperations withoutTransactions;

    public WarehousePlanApplicationService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        CatalogDomainResolutionPort catalogDomainResolutionPort,
        SourceReferenceResolver sourceReferenceResolver,
        AuditService auditService,
        SchemaDriftConsumerReferenceReadPort driftConsumerReferences,
        SchemaDriftDetailsReader schemaDriftDetailsReader,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.catalogDomainResolutionPort = catalogDomainResolutionPort;
        this.sourceReferenceResolver = sourceReferenceResolver;
        this.auditService = auditService;
        this.driftConsumerReferences = driftConsumerReferences;
        this.schemaDriftDetailsReader = schemaDriftDetailsReader;
        this.transactions = new TransactionTemplate(transactionManager);
        TransactionTemplate nonTransactional = new TransactionTemplate(transactionManager);
        nonTransactional.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.withoutTransactions = nonTransactional;
    }

    @Transactional
    public CreateWarehousePlanResult create(String serverTenantId, CreateWarehousePlanCommand command) {
        requireServerTenant(serverTenantId);
        List<DomainIssue> issues = WarehousePlanContract.validateCreate(command);
        if (!issues.isEmpty()) {
            DomainIssue issue = issues.getFirst();
            throw new WarehousePlanException(issue.code(), issue.message(), null);
        }

        String requestHash = createRequestHash(command);
        UUID id = UUID.randomUUID();
        String code = "wp_" + id.toString().replace("-", "");
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, objective, scope, owner_id, owner_department_id,
                onboarding_mode, lifecycle_status, status, version, business_scope_version,
                sources_version, source_mappings_version, policy_version, idempotency_key,
                idempotency_request_hash, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, 1, 1, ?, ?, current_timestamp, current_timestamp)
            on conflict (tenant_id, idempotency_key) do nothing
            """,
            id,
            serverTenantId,
            command.ownerId(),
            code,
            command.name(),
            command.objective(),
            command.scope(),
            command.ownerId(),
            command.ownerDepartmentId(),
            command.onboardingMode().name(),
            LifecycleStatus.DRAFT.name(),
            LifecycleStatus.DRAFT.name(),
            command.idempotencyKey(),
            requestHash
        );
        if (inserted == 0) {
            ExistingCreate existing = findCreateByIdempotencyKey(serverTenantId, command.idempotencyKey());
            if (!requestHash.equals(existing.requestHash())) {
                throw new WarehousePlanException(
                    "WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used for a different warehouse plan request",
                    null
                );
            }
            return replayCreateResult(existing);
        }

        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan_policy (
                plan_id, tenant_id, layer_policy_code, naming_policy_ref, history_policy, default_time_zone,
                conceptual_design_allowed, standard_coverage, quality_gate, created_date, last_modified_date
            ) values (?, ?, null, null, null, null, false, 'KEY_AND_MEASURE', 'BLOCKING', current_timestamp, current_timestamp)
            """,
            id,
            serverTenantId
        );
        for (InitialSourceRef source : sortedInitialSourceRefs(command.initialSourceRefs())) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_source (
                    id, tenant_id, plan_id, source_type, source_id, source_version, confirmation_status,
                    exclusion_reason, created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, 'CANDIDATE', null, current_timestamp, current_timestamp)
                """,
                UUID.randomUUID(),
                serverTenantId,
                id,
                source.sourceType().name(),
                source.sourceId(),
                source.sourceVersion()
            );
        }
        CreateWarehousePlanResult result = createResult(serverTenantId, id, false);
        persistCreateResultSnapshot(serverTenantId, id, result);
        auditService.auditAction(
            "MODELING_WAREHOUSE_PLAN_CREATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "version",
                result.version(),
                "onboardingMode",
                result.plan().onboardingMode().name(),
                "initialSourceCount",
                result.initialSourceBindings().size()
            )
        );
        return result;
    }

    @Transactional(readOnly = true)
    public WarehousePlanHeader get(String serverTenantId, UUID planId) {
        requireServerTenant(serverTenantId);
        if (planId == null) {
            throw notFound();
        }
        return find(serverTenantId, planId).stream().findFirst().orElseThrow(WarehousePlanApplicationService::notFound);
    }

    @Transactional(readOnly = true)
    public List<WarehousePlanHeader> list(String serverTenantId, String lifecycleStatus) {
        requireServerTenant(serverTenantId);
        if (lifecycleStatus == null || lifecycleStatus.isBlank()) {
            return jdbcTemplate.query(
                HEADER_COLUMNS + " where tenant_id = ? order by created_date desc, id",
                WarehousePlanApplicationService::mapHeader,
                serverTenantId
            );
        }
        LifecycleStatus status;
        try {
            status = LifecycleStatus.valueOf(lifecycleStatus.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_LIFECYCLE_STATUS_INVALID",
                "Unknown warehouse plan lifecycle status",
                null
            );
        }
        return jdbcTemplate.query(
            HEADER_COLUMNS + " where tenant_id = ? and lifecycle_status = ? order by created_date desc, id",
            WarehousePlanApplicationService::mapHeader,
            serverTenantId,
            status.name()
        );
    }

    @Transactional
    public WarehousePlanHeader updateHeader(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        UpdatePlanHeaderCommand command
    ) {
        requireServerTenant(serverTenantId);
        if (command == null || command.name() == null || command.name().isBlank() || command.ownerId() == null || command.ownerId().isBlank()) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_HEADER_INVALID",
                "Warehouse plan name and owner are required",
                null
            );
        }
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan
               set name = ?, objective = ?, scope = ?, owner = ?, owner_id = ?, owner_department_id = ?,
                   version = version + 1, last_modified_date = current_timestamp
             where tenant_id = ? and id = ? and version = ?
               and lifecycle_status not in ('PUBLISHED', 'ARCHIVED')
            """,
            command.name(),
            command.objective(),
            command.scope(),
            command.ownerId(),
            command.ownerId(),
            command.ownerDepartmentId(),
            serverTenantId,
            planId,
            expectedVersion
        );
        if (updated == 0) {
            resolveWriteFailure(serverTenantId, planId, expectedVersion);
        }
        WarehousePlanHeader result = get(serverTenantId, planId);
        auditService.auditAction(
            "MODELING_WAREHOUSE_PLAN_HEADER_UPDATE",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of("version", result.version())
        );
        return result;
    }

    @Transactional
    public WarehousePlanHeader archive(String serverTenantId, UUID planId, int expectedVersion) {
        requireServerTenant(serverTenantId);
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan
               set lifecycle_status = 'ARCHIVED', status = 'ARCHIVED', version = version + 1,
                   last_modified_date = current_timestamp
             where tenant_id = ? and id = ? and version = ? and lifecycle_status <> 'ARCHIVED'
            """,
            serverTenantId,
            planId,
            expectedVersion
        );
        if (updated == 0) {
            resolveWriteFailure(serverTenantId, planId, expectedVersion);
        }
        WarehousePlanHeader result = get(serverTenantId, planId);
        auditService.auditAction(
            "MODELING_WAREHOUSE_PLAN_ARCHIVE",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of("version", result.version(), "lifecycleStatus", result.lifecycleStatus().name())
        );
        return result;
    }

    public Versioned<CategoryScopeView> getCategoryScope(String serverTenantId, UUID planId) {
        requireServerTenant(serverTenantId);
        CategoryScopeSnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> {
                get(serverTenantId, planId);
                return new CategoryScopeSnapshot(
                    loadDomainBindings(serverTenantId, planId),
                    readEditUnitVersion(serverTenantId, planId, EditUnit.CATEGORY_SCOPE)
                );
            }),
            "Category scope snapshot is required"
        );
        CategoryScopeView resolved = withoutTransactions.execute(status -> resolveCategoryScope(snapshot.bindings()));
        return new Versioned<>(Objects.requireNonNull(resolved, "Category scope resolution is required"), snapshot.version());
    }

    public Versioned<CategoryScopeView> saveCategoryScope(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        CategoryScopeCommand command
    ) {
        requireServerTenant(serverTenantId);
        List<DomainBinding> bindings = validateCategoryScope(command);
        int observedVersion = Objects.requireNonNull(
            transactions.execute(status -> {
                get(serverTenantId, planId);
                return readEditUnitVersion(serverTenantId, planId, EditUnit.CATEGORY_SCOPE);
            }),
            "Category scope version is required"
        );
        requireExpectedEditVersion(observedVersion, expectedVersion, EditUnit.CATEGORY_SCOPE);
        CategoryScopeView validated = Objects.requireNonNull(
            withoutTransactions.execute(status -> resolveCategoryScope(bindings)),
            "Category scope resolution is required"
        );
        if (
            validated
                .domainBindings()
                .stream()
                .anyMatch(binding ->
                    binding.confirmationStatus() != ConfirmationStatus.EXCLUDED &&
                    binding.resolutionStatus() == CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN
                )
        ) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_CATEGORY_FORBIDDEN",
                "A requested business category is not accessible",
                null,
                EditUnit.CATEGORY_SCOPE
            );
        }
        if (
            validated
                .domainBindings()
                .stream()
                .anyMatch(binding ->
                    binding.confirmationStatus() != ConfirmationStatus.EXCLUDED &&
                    (binding.resolutionStatus() == CatalogDomainResolutionPort.ResolutionStatus.MISSING ||
                        binding.resolutionStatus() == CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED)
                )
        ) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_CATEGORY_INVALID",
                "A requested business category is missing or archived",
                null,
                EditUnit.CATEGORY_SCOPE
            );
        }

        return Objects.requireNonNull(
            transactions.execute(status -> {
                casEditUnit(serverTenantId, planId, expectedVersion, EditUnit.CATEGORY_SCOPE);
                jdbcTemplate.update(
                    "delete from modeling_warehouse_plan_domain where tenant_id = ? and plan_id = ?",
                    serverTenantId,
                    planId
                );
                for (DomainBinding binding : bindings) {
                    jdbcTemplate.update(
                        """
                        insert into modeling_warehouse_plan_domain
                            (id, tenant_id, plan_id, domain_id, confirmation_status, last_validated_at,
                             created_date, last_modified_date)
                        values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                        UUID.randomUUID(),
                        serverTenantId,
                        planId,
                        binding.domainId(),
                        binding.confirmationStatus().name(),
                        Timestamp.from(validated.lastValidatedAt())
                    );
                }
                auditService.auditAction(
                    "MODELING_WAREHOUSE_CATEGORY_SCOPE_SAVE",
                    AuditStage.SUCCESS,
                    planId.toString(),
                    Map.of(
                        "version",
                        expectedVersion + 1,
                        "bindingCount",
                        bindings.size(),
                        "readiness",
                        validated.readiness().name(),
                        "lastValidatedAt",
                        validated.lastValidatedAt().toString()
                    )
                );
                return new Versioned<>(validated, expectedVersion + 1);
            }),
            "Saved category scope is required"
        );
    }

    @Transactional(readOnly = true)
    public Versioned<PlanningPolicyView> getPlanningPolicy(String serverTenantId, UUID planId) {
        requireServerTenant(serverTenantId);
        get(serverTenantId, planId);
        int version = readEditUnitVersion(serverTenantId, planId, EditUnit.POLICY);
        PlanningPolicyCommand policy = inferUniqueDefaultBusinessCategory(
            loadPlanningPolicyCommand(serverTenantId, planId),
            loadDomainBindings(serverTenantId, planId)
        );
        return new Versioned<>(WarehousePlanContract.evaluatePlanningPolicy(policy), version);
    }

    @Transactional
    public Versioned<PlanningPolicyView> savePlanningPolicy(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        PlanningPolicyCommand command
    ) {
        requireServerTenant(serverTenantId);
        get(serverTenantId, planId);
        boolean planningContextRequested = hasPlanningContextFields(command);
        PlanningPolicyCommand mergedCommand = mergeGovernancePolicy(
            command,
            loadPlanningPolicyCommand(serverTenantId, planId)
        );
        List<DomainBinding> domainBindings = loadDomainBindings(serverTenantId, planId);
        mergedCommand = inferUniqueDefaultBusinessCategory(mergedCommand, domainBindings);
        PlanningPolicyView view = WarehousePlanContract.evaluatePlanningPolicy(mergedCommand);
        if (WarehousePlanContract.hasInvalidPolicyValues(view)) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_POLICY_INVALID",
                view.issues().stream().map(DomainIssue::code).collect(java.util.stream.Collectors.joining(",")),
                null,
                EditUnit.POLICY
            );
        }
        if (planningContextRequested) {
            CategoryScopeView categoryScope = Objects.requireNonNull(
                withoutTransactions.execute(status -> resolveCategoryScope(domainBindings)),
                "Category scope resolution is required"
            );
            validatePlanningContextPolicy(view, categoryScope);
        }

        casEditUnit(serverTenantId, planId, expectedVersion, EditUnit.POLICY);
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan_policy
               set layer_policy_code = ?, naming_policy_ref = ?, history_policy = ?, default_time_zone = ?,
                   conceptual_design_allowed = ?, standard_coverage = ?, quality_gate = ?,
                   business_category_mode = ?, default_business_category_id = ?, business_process_mode = ?,
                   last_modified_date = current_timestamp
             where tenant_id = ? and plan_id = ?
            """,
            enumName(view.layerScheme()),
            enumName(view.namingPolicy()),
            enumName(view.historyPolicy()),
            view.defaultTimeZone(),
            view.conceptualDesignAllowed(),
            enumName(view.standardCoverage()),
            enumName(view.qualityGate()),
            enumName(view.businessCategoryMode()),
            view.defaultBusinessCategoryId(),
            enumName(view.businessProcessMode()),
            serverTenantId,
            planId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_policy
                    (plan_id, tenant_id, layer_policy_code, naming_policy_ref, history_policy, default_time_zone,
                     conceptual_design_allowed, standard_coverage, quality_gate, business_category_mode,
                     default_business_category_id, business_process_mode, created_date, last_modified_date)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                planId,
                serverTenantId,
                enumName(view.layerScheme()),
                enumName(view.namingPolicy()),
                enumName(view.historyPolicy()),
                view.defaultTimeZone(),
                view.conceptualDesignAllowed(),
                enumName(view.standardCoverage()),
                enumName(view.qualityGate()),
                enumName(view.businessCategoryMode()),
                view.defaultBusinessCategoryId(),
                enumName(view.businessProcessMode())
            );
        }
        auditService.auditAction(
            "MODELING_WAREHOUSE_POLICY_SAVE",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of("version", expectedVersion + 1, "readiness", view.readiness().name())
        );
        return new Versioned<>(view, expectedVersion + 1);
    }

    @Transactional
    public Versioned<BusinessScope> saveBusinessScope(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        BusinessScope value
    ) {
        requireServerTenant(serverTenantId);
        if (value == null) {
            throw invalidEditUnit(EditUnit.BUSINESS_SCOPE, "Business scope is required");
        }
        validateBusinessScope(value);
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan
               set business_scope_confirmed = ?, business_scope_version = business_scope_version + 1,
                   last_modified_date = current_timestamp
             where tenant_id = ? and id = ? and business_scope_version = ?
               and lifecycle_status not in ('PUBLISHED', 'ARCHIVED')
            """,
            value.confirmed(),
            serverTenantId,
            planId,
            expectedVersion
        );
        if (updated == 0) {
            resolveEditUnitFailure(serverTenantId, planId, expectedVersion, EditUnit.BUSINESS_SCOPE);
        }

        jdbcTemplate.update("delete from modeling_warehouse_plan_metric_need where tenant_id = ? and plan_id = ?", serverTenantId, planId);
        jdbcTemplate.update("delete from modeling_warehouse_plan_process where tenant_id = ? and plan_id = ?", serverTenantId, planId);
        jdbcTemplate.update("delete from modeling_warehouse_plan_domain where tenant_id = ? and plan_id = ?", serverTenantId, planId);

        for (DomainBinding binding : value.domainBindings()) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_domain
                    (id, tenant_id, plan_id, domain_id, confirmation_status, created_date, last_modified_date)
                values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                UUID.randomUUID(),
                serverTenantId,
                planId,
                binding.domainId(),
                binding.confirmationStatus().name()
            );
        }
        for (ProcessBinding binding : value.processBindings()) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_process
                    (id, tenant_id, plan_id, process_id, domain_id, process_shape, confirmation_status, created_date, last_modified_date)
                values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                UUID.randomUUID(),
                serverTenantId,
                planId,
                binding.processId(),
                binding.domainId(),
                binding.processShape(),
                binding.confirmationStatus().name()
            );
        }
        for (MetricRequirement requirement : value.metricRequirements()) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_metric_need
                    (id, tenant_id, plan_id, metric_ref, name, definition, domain_id, process_id,
                     confirmation_status, created_date, last_modified_date)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                requirement.id() == null ? UUID.randomUUID() : requirement.id(),
                serverTenantId,
                planId,
                requirement.metricRef(),
                requirement.name(),
                requirement.definition(),
                requirement.domainId(),
                requirement.processId(),
                requirement.confirmationStatus().name()
            );
        }
        Versioned<BusinessScope> result = new Versioned<>(value, expectedVersion + 1);
        auditService.auditAction(
            "MODELING_WAREHOUSE_BUSINESS_SCOPE_SAVE",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of(
                "version",
                result.version(),
                "confirmed",
                value.confirmed(),
                "domainCount",
                value.domainBindings().size(),
                "processCount",
                value.processBindings().size(),
                "metricRequirementCount",
                value.metricRequirements().size()
            )
        );
        return result;
    }

    public SourceInventoryView getSources(
        String serverTenantId,
        UUID planId,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        return getSources(serverTenantId, planId, accessContext, false);
    }

    public SourceInventoryView getSources(
        String serverTenantId,
        UUID planId,
        SourceReferenceResolver.AccessContext accessContext,
        boolean allowActions
    ) {
        requireServerTenant(serverTenantId);
        SourceInventorySnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> loadSourceInventorySnapshot(serverTenantId, planId)),
            "Source inventory snapshot is required"
        );
        return Objects.requireNonNull(
            withoutTransactions.execute(status -> resolveSourceInventory(serverTenantId, snapshot, accessContext, allowActions)),
            "Source inventory resolution is required"
        );
    }

    public SourceInventoryView getSources(
        String serverTenantId,
        UUID planId,
        SourceReferenceResolver.AccessContext accessContext,
        int page,
        int size,
        boolean allowActions
    ) {
        requireServerTenant(serverTenantId);
        validateSourcePage(page, size);
        PagedSourceInventorySnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> loadPagedSourceInventorySnapshot(serverTenantId, planId, page, size)),
            "Paged source inventory snapshot is required"
        );
        SourceInventoryView resolved = Objects.requireNonNull(
            withoutTransactions.execute(status ->
                resolveSourceInventory(serverTenantId, snapshot.inventory(), accessContext, allowActions)
            ),
            "Source inventory resolution is required"
        );
        int totalPages = snapshot.totalElements() == 0
            ? 0
            : (int) Math.ceil((double) snapshot.totalElements() / size);
        return new SourceInventoryView(
            resolved.bindings(),
            resolved.readiness(),
            resolved.issues(),
            resolved.version(),
            resolved.etag(),
            resolved.checkedAt(),
            page,
            size,
            snapshot.totalElements(),
            totalPages
        );
    }

    public SourceInventoryView saveSources(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        SourceInventoryCommand command,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        requireServerTenant(serverTenantId);
        List<DomainIssue> issues = WarehousePlanContract.validateSourceInventoryCommand(command);
        if (!issues.isEmpty()) {
            DomainIssue issue = issues.getFirst();
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_SOURCE_INVENTORY_INVALID",
                issue.code() + ": " + issue.message(),
                null,
                EditUnit.SOURCES
            );
        }
        SourceInventorySnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> loadSourceInventorySnapshot(serverTenantId, planId)),
            "Source inventory snapshot is required"
        );
        PreparedSourceInventory prepared = Objects.requireNonNull(
            withoutTransactions.execute(status ->
                prepareSourceInventory(serverTenantId, snapshot.rows(), command, accessContext)
            ),
            "Resolved source inventory is required"
        );
        if (snapshot.version() != expectedVersion) {
            if (snapshot.version() == expectedVersion + 1 && prepared.allReconfirmationsReplayed()) {
                return Objects.requireNonNull(
                    withoutTransactions.execute(status -> resolveSourceInventory(serverTenantId, snapshot, accessContext, true)),
                    "Replayed source inventory is required"
                );
            }
            requireExpectedEditVersion(snapshot.version(), expectedVersion, EditUnit.SOURCES);
        }
        if (prepared.allReconfirmationsReplayed()) {
            return Objects.requireNonNull(
                withoutTransactions.execute(status -> resolveSourceInventory(serverTenantId, snapshot, accessContext, true)),
                "Replayed source inventory is required"
            );
        }
        return Objects.requireNonNull(
            transactions.execute(status ->
                persistSourceInventory(
                    serverTenantId,
                    planId,
                    expectedVersion,
                    snapshot.plan(),
                    prepared,
                    serverAccessContext(serverTenantId, accessContext)
                )
            ),
            "Saved source inventory is required"
        );
    }

    private PreparedSourceInventory prepareSourceInventory(
        String serverTenantId,
        List<SourceRow> existingRows,
        SourceInventoryCommand command,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        Map<UUID, SourceRow> existingById = new HashMap<>();
        Map<String, SourceRow> existingByIdentity = new HashMap<>();
        for (SourceRow row : existingRows) {
            existingById.put(row.bindingId(), row);
            existingByIdentity.put(sourceKey(row.sourceType(), row.sourceId()), row);
        }

        Instant checkedAt = Instant.now();
        SourceReferenceResolver.AccessContext serverContext = serverAccessContext(serverTenantId, accessContext);
        List<ResolvedSourceWrite> writes = new ArrayList<>();
        Set<UUID> retainedIds = new HashSet<>();
        for (SourceBindingCommand binding : command.bindings()) {
            SourceRow existing = binding.bindingId() == null ? null : existingById.get(binding.bindingId());
            if (binding.bindingId() != null && existing == null) {
                throw invalidSourceInventory("SOURCE_BINDING_NOT_FOUND", "bindingId does not belong to this warehouse plan");
            }

            boolean identityProvided = binding.sourceType() != null || binding.locator() != null;
            SourceType sourceType;
            SourceLocator locator;
            String sourceId;
            if (identityProvided) {
                sourceType = binding.sourceType();
                locator = binding.locator();
                sourceId = WarehousePlanContract.canonicalSourceId(sourceType, locator);
                if (existing == null) {
                    existing = existingByIdentity.get(sourceKey(sourceType, sourceId));
                }
            } else {
                sourceType = existing.sourceType();
                locator = readLocator(existing);
                sourceId = existing.sourceId();
            }
            if (
                existing != null &&
                (existing.sourceType() != sourceType || !existing.sourceId().equals(sourceId))
            ) {
                throw invalidSourceInventory("SOURCE_BINDING_IDENTITY_IMMUTABLE", "A binding cannot be reassigned to another source");
            }

            UUID bindingId = existing == null ? UUID.randomUUID() : existing.bindingId();
            if (!retainedIds.add(bindingId)) {
                throw invalidSourceInventory("SOURCE_BINDING_DUPLICATE", "A source binding may only appear once");
            }
            SourceReferenceResolver.ResolvedSource resolution = locator == null
                ? SourceReferenceResolver.ResolvedSource.providerError()
                : resolveSource(sourceType, locator, serverContext);
            if (existing == null && resolution.status() != SourceReferenceResolver.ResolutionStatus.AVAILABLE) {
                throw invalidSourceInventory(
                    "SOURCE_NOT_AVAILABLE",
                    "Source cannot be registered until it is available"
                );
            }
            validateExplicitSourceAction(binding.action(), existing, resolution);
            boolean reconfirmation = binding.action() == SourceAction.RECONFIRM;
            boolean replayed = false;
            String confirmedVersion;
            if (reconfirmation) {
                if (existing == null || resolution.status() != SourceReferenceResolver.ResolutionStatus.AVAILABLE) {
                    throw invalidSourceInventory(
                        "SOURCE_RECONFIRM_NOT_ALLOWED",
                        "The source must be available before it can be reconfirmed"
                    );
                }
                if (!Objects.equals(binding.expectedCurrentVersion(), resolution.resolvedVersion())) {
                    throw sourceVersionChanged();
                }
                if (Objects.equals(existing.sourceVersion(), binding.expectedCurrentVersion())) {
                    replayed = true;
                } else if (!Objects.equals(existing.sourceVersion(), binding.expectedConfirmedVersion())) {
                    throw sourceVersionChanged();
                }
                if (!replayed) {
                    requireCompatibleReconfirmation(
                        sourceDriftEvidence(sourceType, locator, freshness(existing.sourceVersion(), resolution))
                    );
                }
                confirmedVersion = binding.expectedCurrentVersion();
            } else {
                confirmedVersion = existing == null ? resolution.resolvedVersion() : existing.sourceVersion();
            }
            boolean stateAlreadyApplied =
                existing != null &&
                existing.confirmationStatus() == binding.confirmationStatus() &&
                Objects.equals(existing.exclusionReason(), binding.exclusionReason()) &&
                Objects.equals(existing.sourceVersion(), confirmedVersion);
            writes.add(
                new ResolvedSourceWrite(
                    bindingId,
                    sourceType,
                    locator,
                    sourceId,
                    binding.confirmationStatus(),
                    binding.exclusionReason(),
                    confirmedVersion,
                    resolution,
                    checkedAt,
                    binding.action(),
                    existing == null ? null : existing.sourceVersion(),
                    replayed,
                    stateAlreadyApplied
                )
            );
        }
        if (existingRows.stream().anyMatch(row -> !retainedIds.contains(row.bindingId()))) {
            throw invalidSourceInventory(
                "SOURCE_REMOVAL_REQUIRES_EXCLUSION",
                "Existing sources must be retained and marked EXCLUDED to preserve audit history"
            );
        }
        return new PreparedSourceInventory(
            writes,
            checkedAt,
            accessContext == null ? null : accessContext.actorId(),
            UUID.randomUUID().toString()
        );
    }

    private static void validateExplicitSourceAction(
        SourceAction action,
        SourceRow existing,
        SourceReferenceResolver.ResolvedSource resolution
    ) {
        if (action == null) {
            return;
        }
        switch (action) {
            case CONFIRM -> {
                if (
                    existing == null ||
                    existing.confirmationStatus() != ConfirmationStatus.CANDIDATE ||
                    resolution.status() != SourceReferenceResolver.ResolutionStatus.AVAILABLE
                ) {
                    throw invalidSourceInventory(
                        "SOURCE_CONFIRM_NOT_ALLOWED",
                        "Only an available candidate source can be confirmed"
                    );
                }
            }
            case EXCLUDE -> {
                if (existing == null || existing.confirmationStatus() == ConfirmationStatus.EXCLUDED) {
                    throw invalidSourceInventory(
                        "SOURCE_EXCLUDE_NOT_ALLOWED",
                        "Only an active source binding can be excluded"
                    );
                }
            }
            case RECONFIRM -> {
                if (
                    existing == null ||
                    existing.confirmationStatus() != ConfirmationStatus.CONFIRMED ||
                    resolution.status() != SourceReferenceResolver.ResolutionStatus.AVAILABLE
                ) {
                    throw invalidSourceInventory(
                        "SOURCE_RECONFIRM_NOT_ALLOWED",
                        "The source must be confirmed and available before it can be reconfirmed"
                    );
                }
            }
        }
    }

    private static void requireCompatibleReconfirmation(SourceDriftEvidence evidence) {
        if (evidence.impact() == SourceChangeImpact.COMPATIBLE) {
            return;
        }
        if (evidence.impact() == SourceChangeImpact.BREAKING) {
            throw invalidSourceInventory(
                "SOURCE_RECONFIRM_BREAKING_CHANGE",
                "Repair the affected field mappings before reconfirming this source"
            );
        }
        throw invalidSourceInventory(
            "SOURCE_RECONFIRM_REVIEW_REQUIRED",
            "Review the source changes before reconfirming this source"
        );
    }

    private SourceDriftEvidence revalidateReconfirmation(
        String serverTenantId,
        UUID planId,
        ResolvedSourceWrite write,
        SourceReferenceResolver.AccessContext serverContext
    ) {
        List<String> storedVersions = jdbcTemplate.query(
            "select source_version from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ? and id = ?",
            (row, rowNumber) -> row.getString("source_version"),
            serverTenantId,
            planId,
            write.bindingId()
        );
        if (
            storedVersions.size() != 1 ||
            !Objects.equals(storedVersions.getFirst(), write.previousConfirmedVersion())
        ) {
            throw sourceVersionChanged();
        }
        SourceReferenceResolver.ResolvedSource resolution = write.locator() == null
            ? SourceReferenceResolver.ResolvedSource.providerError()
            : resolveSource(write.sourceType(), write.locator(), serverContext);
        if (
            resolution.status() != SourceReferenceResolver.ResolutionStatus.AVAILABLE ||
            !Objects.equals(write.confirmedVersion(), resolution.resolvedVersion())
        ) {
            throw sourceVersionChanged();
        }
        SourceDriftEvidence evidence = sourceDriftEvidence(
            write.sourceType(),
            write.locator(),
            freshness(write.previousConfirmedVersion(), resolution)
        );
        requireCompatibleReconfirmation(evidence);
        return evidence;
    }

    private SourceInventoryView persistSourceInventory(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        WarehousePlanHeader plan,
        PreparedSourceInventory prepared,
        SourceReferenceResolver.AccessContext serverContext
    ) {
        casEditUnit(serverTenantId, planId, expectedVersion, EditUnit.SOURCES);
        for (ResolvedSourceWrite write : prepared.writes()) {
            if (write.reconfirmationReplayed()) {
                continue;
            }
            SourceDriftEvidence reconfirmedEvidence = write.action() == SourceAction.RECONFIRM
                ? revalidateReconfirmation(serverTenantId, planId, write, serverContext)
                : SourceDriftEvidence.none();
            String locatorJson = write.locator() == null ? null : writeLocator(write.locator());
            int updated = jdbcTemplate.update(
                """
                update modeling_warehouse_plan_source
                   set source_type = ?, source_id = ?, source_version = ?,
                       locator_json = coalesce(cast(? as jsonb), locator_json),
                       confirmation_status = ?, exclusion_reason = ?, resolution_status = ?, last_validated_at = ?,
                       last_modified_date = current_timestamp
                 where tenant_id = ? and plan_id = ? and id = ?
                """,
                write.sourceType().name(),
                write.sourceId(),
                write.confirmedVersion(),
                locatorJson,
                write.confirmationStatus().name(),
                write.exclusionReason(),
                write.resolution().status().name(),
                Timestamp.from(write.checkedAt()),
                serverTenantId,
                planId,
                write.bindingId()
            );
            if (updated == 0) {
                jdbcTemplate.update(
                    """
                    insert into modeling_warehouse_plan_source
                        (id, tenant_id, plan_id, source_type, source_id, source_version, locator_json,
                         confirmation_status, exclusion_reason, resolution_status, last_validated_at,
                         created_date, last_modified_date)
                    values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, current_timestamp, current_timestamp)
                    """,
                    write.bindingId(),
                    serverTenantId,
                    planId,
                    write.sourceType().name(),
                    write.sourceId(),
                    write.confirmedVersion(),
                    locatorJson,
                    write.confirmationStatus().name(),
                    write.exclusionReason(),
                    write.resolution().status().name(),
                    Timestamp.from(write.checkedAt())
                );
            }
            if (write.action() == SourceAction.RECONFIRM) {
                closeSourceDriftTickets(reconfirmedEvidence.eventIds(), prepared.actorId());
            }
        }

        SourceInventoryView result = WarehousePlanContract.evaluateSourceInventory(
            prepared.writes().stream().map(write -> toSourceBindingView(write, true)).toList(),
            plan.onboardingMode(),
            expectedVersion + 1,
            prepared.checkedAt()
        );
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("tenantId", serverTenantId);
        auditPayload.put("actor", prepared.actorId());
        auditPayload.put("correlationId", prepared.correlationId());
        auditPayload.put("version", result.version());
        auditPayload.put("bindingCount", result.bindings().size());
        auditPayload.put("readiness", result.readiness().name());
        auditPayload.put("checkedAt", result.checkedAt().toString());
        auditPayload.put(
            "reconfirmations",
            prepared
                .writes()
                .stream()
                .filter(write -> write.action() == SourceAction.RECONFIRM)
                .map(write -> {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("bindingId", write.bindingId());
                    evidence.put("assetKey", sourceAssetKey(write));
                    evidence.put("oldVersion", write.previousConfirmedVersion());
                    evidence.put("newVersion", write.confirmedVersion());
                    return evidence;
                })
                .toList()
        );
        auditService.auditAction(
            "MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE",
            AuditStage.SUCCESS,
            planId.toString(),
            auditPayload
        );
        return result;
    }

    @Transactional
    public Versioned<List<SourceBusinessMapping>> saveSourceMappings(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        List<SourceBusinessMapping> value
    ) {
        requireServerTenant(serverTenantId);
        List<SourceBusinessMapping> mappings = value == null ? List.of() : List.copyOf(value);
        validateMappings(serverTenantId, planId, mappings);
        casEditUnit(serverTenantId, planId, expectedVersion, EditUnit.SOURCE_MAPPINGS);

        jdbcTemplate.update(
            "delete from modeling_warehouse_plan_source_mapping where tenant_id = ? and plan_id = ?",
            serverTenantId,
            planId
        );
        for (SourceBusinessMapping mapping : mappings) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_source_mapping
                    (id, tenant_id, plan_id, source_binding_id, domain_id, process_id, mapping_status,
                     notes, created_date, last_modified_date)
                values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                mapping.id() == null ? UUID.randomUUID() : mapping.id(),
                serverTenantId,
                planId,
                mapping.sourceBindingId(),
                mapping.domainId(),
                mapping.processId(),
                mapping.confirmationStatus().name(),
                mapping.notes()
            );
        }
        Versioned<List<SourceBusinessMapping>> result = new Versioned<>(mappings, expectedVersion + 1);
        auditService.auditAction(
            "MODELING_WAREHOUSE_SOURCE_MAPPINGS_SAVE",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of("version", result.version(), "mappingCount", mappings.size())
        );
        return result;
    }

    @Transactional
    public Versioned<PlanningPolicy> savePolicy(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        PlanningPolicy value
    ) {
        requireServerTenant(serverTenantId);
        if (!completePolicy(value)) {
            throw invalidEditUnit(EditUnit.POLICY, "Layer, history and time-zone policies are required");
        }
        casEditUnit(serverTenantId, planId, expectedVersion, EditUnit.POLICY);
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan_policy
               set layer_policy_code = ?, naming_policy_ref = ?, history_policy = ?, default_time_zone = ?,
                   last_modified_date = current_timestamp
             where tenant_id = ? and plan_id = ?
            """,
            value.layerPolicyCode(),
            value.namingPolicyRef(),
            value.historyPolicy(),
            value.defaultTimeZone(),
            serverTenantId,
            planId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_policy
                    (plan_id, tenant_id, layer_policy_code, naming_policy_ref, history_policy, default_time_zone,
                     standard_coverage, quality_gate, created_date, last_modified_date)
                values (?, ?, ?, ?, ?, ?, 'ALL_FIELDS', 'BLOCKING', current_timestamp, current_timestamp)
                """,
                planId,
                serverTenantId,
                value.layerPolicyCode(),
                value.namingPolicyRef(),
                value.historyPolicy(),
                value.defaultTimeZone()
            );
        }
        return new Versioned<>(value, expectedVersion + 1);
    }

    public PlanningBaseline getBaseline(String serverTenantId, UUID planId) {
        requireServerTenant(serverTenantId);
        BaselineSnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> loadBaselineSnapshot(serverTenantId, planId)),
            "Planning baseline snapshot is required"
        );
        return resolveBaseline(
            serverTenantId,
            snapshot,
            new SourceReferenceResolver.AccessContext(
                serverTenantId,
                snapshot.plan().ownerId(),
                snapshot.plan().ownerDepartmentId()
            )
        );
    }

    public PlanningBaseline getBaseline(
        String serverTenantId,
        UUID planId,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        requireServerTenant(serverTenantId);
        BaselineSnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> loadBaselineSnapshot(serverTenantId, planId)),
            "Planning baseline snapshot is required"
        );
        return resolveBaseline(serverTenantId, snapshot, accessContext);
    }

    public PlanningBaseline confirmBaseline(
        String serverTenantId,
        UUID planId,
        int expectedPlanHeadVersion,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        requireServerTenant(serverTenantId);
        BaselineSnapshot snapshot = Objects.requireNonNull(
            transactions.execute(status -> loadBaselineSnapshot(serverTenantId, planId)),
            "Planning baseline snapshot is required"
        );
        if (snapshot.plan().version() != expectedPlanHeadVersion) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_VERSION_CONFLICT",
                "Warehouse plan was changed by another operation",
                snapshot.plan().version()
            );
        }
        if (!WarehousePlanContract.canTransition(snapshot.plan().lifecycleStatus(), LifecycleStatus.BASELINE_READY)) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT",
                "Warehouse plan lifecycle does not allow baseline confirmation",
                snapshot.plan().version()
            );
        }
        PlanningBaseline baseline = resolveBaseline(serverTenantId, snapshot, accessContext);
        if (!baseline.ready()) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_BASELINE_INCOMPLETE",
                "Warehouse planning baseline is incomplete: " + String.join(",", baseline.missingCodes()),
                null
            );
        }
        return Objects.requireNonNull(
            transactions.execute(status -> confirmBaselineSnapshot(serverTenantId, planId, expectedPlanHeadVersion, snapshot, baseline)),
            "Confirmed planning baseline is required"
        );
    }

    private PlanningBaseline confirmBaselineSnapshot(
        String serverTenantId,
        UUID planId,
        int expectedPlanHeadVersion,
        BaselineSnapshot snapshot,
        PlanningBaseline baseline
    ) {
        WarehousePlanHeader current = lockPlan(serverTenantId, planId);
        if (current.version() != expectedPlanHeadVersion) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_VERSION_CONFLICT",
                "Warehouse plan was changed by another operation",
                current.version()
            );
        }
        if (!WarehousePlanContract.canTransition(current.lifecycleStatus(), LifecycleStatus.BASELINE_READY)) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT",
                "Warehouse plan lifecycle does not allow baseline confirmation",
                current.version()
            );
        }
        if (
            readEditUnitVersion(serverTenantId, planId, EditUnit.CATEGORY_SCOPE) != snapshot.categoryScopeVersion() ||
            readEditUnitVersion(serverTenantId, planId, EditUnit.SOURCES) != snapshot.sourceInventory().version() ||
            readEditUnitVersion(serverTenantId, planId, EditUnit.POLICY) != snapshot.policyVersion()
        ) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_BASELINE_STALE",
                "Warehouse plan baseline changed while its external references were being validated",
                current.version()
            );
        }
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan
               set lifecycle_status = 'BASELINE_READY', status = 'BASELINE_READY', version = version + 1,
                   last_modified_date = current_timestamp
             where tenant_id = ? and id = ? and version = ? and lifecycle_status = 'DRAFT'
            """,
            serverTenantId,
            planId,
            expectedPlanHeadVersion
        );
        if (updated == 0) {
            resolveWriteFailure(serverTenantId, planId, expectedPlanHeadVersion);
        }
        auditService.auditAction(
            "MODELING_WAREHOUSE_BASELINE_CONFIRM",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of(
                "version",
                expectedPlanHeadVersion + 1,
                "lifecycleStatus",
                LifecycleStatus.BASELINE_READY.name()
            )
        );
        return baseline;
    }

    private static void validateBusinessScope(BusinessScope scope) {
        Set<UUID> domainIds = new HashSet<>();
        for (DomainBinding binding : scope.domainBindings()) {
            if (
                binding == null ||
                binding.domainId() == null ||
                binding.confirmationStatus() == null ||
                !domainIds.add(binding.domainId())
            ) {
                throw invalidEditUnit(EditUnit.BUSINESS_SCOPE, "Business domains must be unique and complete");
            }
        }

        Set<String> processIds = new HashSet<>();
        for (ProcessBinding binding : scope.processBindings()) {
            if (
                binding == null ||
                isBlank(binding.processId()) ||
                binding.domainId() == null ||
                binding.confirmationStatus() == null ||
                !processIds.add(binding.processId())
            ) {
                throw invalidEditUnit(EditUnit.BUSINESS_SCOPE, "Business processes must be unique and complete");
            }
        }

        Set<UUID> metricIds = new HashSet<>();
        for (MetricRequirement requirement : scope.metricRequirements()) {
            if (
                requirement == null ||
                isBlank(requirement.name()) ||
                requirement.confirmationStatus() == null ||
                (requirement.id() != null && !metricIds.add(requirement.id()))
            ) {
                throw invalidEditUnit(EditUnit.BUSINESS_SCOPE, "Metric requirements must be unique and complete");
            }
        }
    }

    private void validateMappings(String tenantId, UUID planId, List<SourceBusinessMapping> mappings) {
        Set<UUID> availableSourceIds = new HashSet<>(
            jdbcTemplate.query(
                "select id from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
                (row, rowNumber) -> row.getObject("id", UUID.class),
                tenantId,
                planId
            )
        );
        Set<UUID> mappingIds = new HashSet<>();
        for (SourceBusinessMapping mapping : mappings) {
            if (
                mapping == null ||
                mapping.sourceBindingId() == null ||
                mapping.confirmationStatus() == null ||
                !availableSourceIds.contains(mapping.sourceBindingId()) ||
                (mapping.id() != null && !mappingIds.add(mapping.id()))
            ) {
                throw invalidEditUnit(EditUnit.SOURCE_MAPPINGS, "Source mappings must reference sources in the current plan");
            }
            if (
                mapping.confirmationStatus() == ConfirmationStatus.CONFIRMED &&
                (mapping.domainId() == null || isBlank(mapping.processId()))
            ) {
                throw invalidEditUnit(EditUnit.SOURCE_MAPPINGS, "Confirmed source mappings require a domain and process");
            }
        }
    }

    private void casEditUnit(String tenantId, UUID planId, int expectedVersion, EditUnit editUnit) {
        String versionColumn = versionColumn(editUnit);
        int updated = jdbcTemplate.update(
            ("""
            update modeling_warehouse_plan
               set %s = %s + 1, last_modified_date = current_timestamp
             where tenant_id = ? and id = ? and %s = ?
               and lifecycle_status not in ('PUBLISHED', 'ARCHIVED')
            """).formatted(versionColumn, versionColumn, versionColumn),
            tenantId,
            planId,
            expectedVersion
        );
        if (updated == 0) {
            resolveEditUnitFailure(tenantId, planId, expectedVersion, editUnit);
        }
    }

    private void resolveEditUnitFailure(String tenantId, UUID planId, int expectedVersion, EditUnit editUnit) {
        WarehousePlanHeader current = find(tenantId, planId).stream().findFirst().orElseThrow(WarehousePlanApplicationService::notFound);
        Integer currentVersion = jdbcTemplate.queryForObject(
            "select " + versionColumn(editUnit) + " from modeling_warehouse_plan where tenant_id = ? and id = ?",
            Integer.class,
            tenantId,
            planId
        );
        if (current.lifecycleStatus() == LifecycleStatus.PUBLISHED || current.lifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT",
                "Warehouse plan lifecycle does not allow this operation",
                currentVersion,
                editUnit
            );
        }
        throw new WarehousePlanException(
            "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT",
            "Warehouse plan edit unit was changed by another operation",
            currentVersion,
            editUnit
        );
    }

    private int readEditUnitVersion(String tenantId, UUID planId, EditUnit editUnit) {
        Integer version = jdbcTemplate.queryForObject(
            "select " + versionColumn(editUnit) + " from modeling_warehouse_plan where tenant_id = ? and id = ?",
            Integer.class,
            tenantId,
            planId
        );
        if (version == null) {
            throw notFound();
        }
        return version;
    }

    private List<DomainBinding> loadDomainBindings(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            """
            select domain_id, confirmation_status
              from modeling_warehouse_plan_domain
             where tenant_id = ? and plan_id = ?
             order by domain_id
            """,
            (row, rowNumber) ->
                new DomainBinding(
                    row.getObject("domain_id", UUID.class),
                    ConfirmationStatus.valueOf(row.getString("confirmation_status"))
                ),
            tenantId,
            planId
        );
    }

    private List<DomainBinding> validateCategoryScope(CategoryScopeCommand command) {
        if (command == null) {
            throw invalidEditUnit(EditUnit.CATEGORY_SCOPE, "Category scope is required");
        }
        List<DomainBinding> bindings = command.domainBindings();
        Set<UUID> domainIds = new HashSet<>();
        for (DomainBinding binding : bindings) {
            if (
                binding == null ||
                binding.domainId() == null ||
                binding.confirmationStatus() == null ||
                !domainIds.add(binding.domainId())
            ) {
                throw invalidEditUnit(EditUnit.CATEGORY_SCOPE, "Category bindings must be unique and complete");
            }
        }
        return bindings;
    }

    private CategoryScopeView resolveCategoryScope(List<DomainBinding> bindings) {
        Instant validatedAt = Instant.now();
        List<CategoryBindingView> resolved = bindings
            .stream()
            .map(binding -> resolveCategoryBinding(binding, validatedAt))
            .toList();
        return WarehousePlanContract.evaluateCategoryScope(resolved, validatedAt);
    }

    private CategoryBindingView resolveCategoryBinding(DomainBinding binding, Instant validatedAt) {
        CatalogDomainResolutionPort.DomainResolution resolution = catalogDomainResolutionPort.resolve(binding.domainId());
        if (resolution == null) {
            return new CategoryBindingView(
                binding.domainId(),
                binding.confirmationStatus(),
                CatalogDomainResolutionPort.ResolutionStatus.MISSING,
                null,
                null,
                validatedAt
            );
        }
        boolean redact = resolution.status() == CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN;
        return new CategoryBindingView(
            binding.domainId(),
            binding.confirmationStatus(),
            resolution.status() == null ? CatalogDomainResolutionPort.ResolutionStatus.MISSING : resolution.status(),
            redact ? null : resolution.name(),
            redact ? null : resolution.code(),
            validatedAt
        );
    }

    private PlanningPolicyCommand loadPlanningPolicyCommand(String tenantId, UUID planId) {
        return jdbcTemplate
            .query(
                """
                select layer_policy_code, naming_policy_ref, history_policy, default_time_zone,
                       conceptual_design_allowed, standard_coverage, quality_gate,
                       business_category_mode, default_business_category_id, business_process_mode
                  from modeling_warehouse_plan_policy
                 where tenant_id = ? and plan_id = ?
                """,
                (row, rowNumber) ->
                    new PlanningPolicyCommand(
                        row.getString("layer_policy_code"),
                        row.getString("naming_policy_ref"),
                        row.getString("history_policy"),
                        row.getString("default_time_zone"),
                        row.getBoolean("conceptual_design_allowed"),
                        row.getString("standard_coverage"),
                        row.getString("quality_gate"),
                        row.getString("business_category_mode"),
                        row.getObject("default_business_category_id", UUID.class),
                        row.getString("business_process_mode")
                    ),
                tenantId,
                planId
            )
            .stream()
            .findFirst()
            .orElseGet(() ->
                new PlanningPolicyCommand(
                    null,
                    null,
                    null,
                    null,
                    false,
                    "ALL_FIELDS",
                    "BLOCKING",
                    "SINGLE_DEFAULT",
                    null,
                    "AUTO_SELECT_SINGLE"
                )
            );
    }

    private static PlanningPolicyCommand mergeGovernancePolicy(
        PlanningPolicyCommand requested,
        PlanningPolicyCommand stored
    ) {
        PlanningPolicyCommand value = requested == null
            ? new PlanningPolicyCommand(null, null, null, null)
            : requested;
        PlanningPolicyCommand current = stored == null
            ? new PlanningPolicyCommand(
                null,
                null,
                null,
                null,
                false,
                "ALL_FIELDS",
                "BLOCKING",
                "SINGLE_DEFAULT",
                null,
                "AUTO_SELECT_SINGLE"
            )
            : stored;
        return new PlanningPolicyCommand(
            value.layerScheme(),
            value.namingPolicy(),
            value.historyPolicy(),
            value.defaultTimeZone(),
            value.conceptualDesignAllowed(),
            value.standardCoverage() == null ? current.standardCoverage() : value.standardCoverage(),
            value.qualityGate() == null ? current.qualityGate() : value.qualityGate(),
            value.businessCategoryMode() == null ? current.businessCategoryMode() : value.businessCategoryMode(),
            value.defaultBusinessCategoryId() == null
                ? current.defaultBusinessCategoryId()
                : value.defaultBusinessCategoryId(),
            value.businessProcessMode() == null ? current.businessProcessMode() : value.businessProcessMode()
        );
    }

    private static boolean hasPlanningContextFields(PlanningPolicyCommand command) {
        return (
            command != null &&
            (
                command.businessCategoryMode() != null ||
                command.defaultBusinessCategoryId() != null ||
                command.businessProcessMode() != null
            )
        );
    }

    private static PlanningPolicyCommand inferUniqueDefaultBusinessCategory(
        PlanningPolicyCommand policy,
        List<DomainBinding> bindings
    ) {
        PlanningPolicyCommand value = policy == null ? new PlanningPolicyCommand(null, null, null, null) : policy;
        if (
            value.defaultBusinessCategoryId() != null ||
            "MULTI_SELECT".equals(value.businessCategoryMode())
        ) {
            return value;
        }
        List<UUID> confirmed = bindings == null
            ? List.of()
            : bindings
                .stream()
                .filter(binding -> binding.confirmationStatus() == ConfirmationStatus.CONFIRMED)
                .map(DomainBinding::domainId)
                .distinct()
                .toList();
        if (confirmed.size() != 1) {
            return value;
        }
        return new PlanningPolicyCommand(
            value.layerScheme(),
            value.namingPolicy(),
            value.historyPolicy(),
            value.defaultTimeZone(),
            value.conceptualDesignAllowed(),
            value.standardCoverage(),
            value.qualityGate(),
            value.businessCategoryMode(),
            confirmed.getFirst(),
            value.businessProcessMode()
        );
    }

    private static void validatePlanningContextPolicy(PlanningPolicyView policy, CategoryScopeView categoryScope) {
        UUID defaultCategoryId = policy.defaultBusinessCategoryId();
        if (
            policy.businessCategoryMode() == WarehousePlanContract.BusinessCategoryMode.SINGLE_DEFAULT &&
            defaultCategoryId == null
        ) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_DEFAULT_CATEGORY_REQUIRED",
                "defaultBusinessCategoryId must identify one confirmed business category in the current plan",
                null,
                EditUnit.POLICY
            );
        }
        if (defaultCategoryId == null) {
            return;
        }
        CategoryBindingView selected = categoryScope
            .domainBindings()
            .stream()
            .filter(binding -> defaultCategoryId.equals(binding.domainId()))
            .findFirst()
            .orElseThrow(() ->
                new WarehousePlanException(
                    "WAREHOUSE_PLAN_DEFAULT_CATEGORY_OUT_OF_SCOPE",
                    "defaultBusinessCategoryId must belong to the current plan",
                    null,
                    EditUnit.POLICY
                )
            );
        if (
            selected.confirmationStatus() != ConfirmationStatus.CONFIRMED ||
            selected.resolutionStatus() != CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE
        ) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_DEFAULT_CATEGORY_UNAVAILABLE",
                "defaultBusinessCategoryId must be confirmed and available",
                null,
                EditUnit.POLICY
            );
        }
    }

    private BaselineSnapshot loadBaselineSnapshot(String tenantId, UUID planId) {
        WarehousePlanHeader plan = get(tenantId, planId);
        return new BaselineSnapshot(
            plan,
            loadDomainBindings(tenantId, planId),
            readEditUnitVersion(tenantId, planId, EditUnit.CATEGORY_SCOPE),
            loadSourceInventorySnapshot(tenantId, planId, plan),
            loadPlanningPolicyCommand(tenantId, planId),
            readEditUnitVersion(tenantId, planId, EditUnit.POLICY)
        );
    }

    private PlanningBaseline resolveBaseline(
        String tenantId,
        BaselineSnapshot snapshot,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        return Objects.requireNonNull(
            withoutTransactions.execute(status -> {
                CategoryScopeView categoryScope = resolveCategoryScope(snapshot.domainBindings());
                SourceInventoryView sourceInventory = resolveSourceInventory(
                    tenantId,
                    snapshot.sourceInventory(),
                    accessContext
                );
                PlanningPolicyView planningPolicy = WarehousePlanContract.evaluatePlanningPolicy(snapshot.policy());
                return WarehousePlanContract.evaluateBaseline(
                    categoryScope,
                    sourceInventory,
                    planningPolicy,
                    snapshot.plan().onboardingMode()
                );
            }),
            "Resolved planning baseline is required"
        );
    }

    private WarehousePlanHeader lockPlan(String tenantId, UUID planId) {
        return jdbcTemplate
            .query(HEADER_COLUMNS + " where tenant_id = ? and id = ? for update", WarehousePlanApplicationService::mapHeader, tenantId, planId)
            .stream()
            .findFirst()
            .orElseThrow(WarehousePlanApplicationService::notFound);
    }

    private static String versionColumn(EditUnit editUnit) {
        return switch (editUnit) {
            case BUSINESS_SCOPE, CATEGORY_SCOPE -> "business_scope_version";
            case SOURCES -> "sources_version";
            case SOURCE_MAPPINGS -> "source_mappings_version";
            case POLICY -> "policy_version";
            case PLAN_HEAD -> throw new IllegalArgumentException("Plan head uses the aggregate version");
        };
    }

    private static boolean completePolicy(PlanningPolicy policy) {
        return policy != null &&
        !isBlank(policy.layerPolicyCode()) &&
        !isBlank(policy.historyPolicy()) &&
        !isBlank(policy.defaultTimeZone());
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static WarehousePlanException invalidEditUnit(EditUnit editUnit, String message) {
        return new WarehousePlanException("WAREHOUSE_PLAN_EDIT_UNIT_INVALID", message, null, editUnit);
    }

    private CreateWarehousePlanResult createResult(String tenantId, UUID planId, boolean replayed) {
        WarehousePlanHeader plan = get(tenantId, planId);
        return new CreateWarehousePlanResult(
            plan.id(),
            plan,
            plan.version(),
            "\"plan-head:" + plan.version() + "\"",
            loadSources(tenantId, planId),
            "/modeling/plans/" + plan.id() + "/baseline",
            replayed
        );
    }

    private SourceInventoryView resolveSourceInventory(
        String tenantId,
        SourceInventorySnapshot snapshot,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        return resolveSourceInventory(tenantId, snapshot, accessContext, false);
    }

    private SourceInventoryView resolveSourceInventory(
        String tenantId,
        SourceInventorySnapshot snapshot,
        SourceReferenceResolver.AccessContext accessContext,
        boolean allowActions
    ) {
        Instant checkedAt = Instant.now();
        SourceReferenceResolver.AccessContext serverContext = serverAccessContext(tenantId, accessContext);
        List<SourceBindingView> bindings = snapshot
            .rows()
            .stream()
            .map(row -> {
                SourceLocator locator = readLocator(row);
                SourceReferenceResolver.ResolvedSource resolution = locator == null
                    ? SourceReferenceResolver.ResolvedSource.providerError()
                    : resolveSource(row.sourceType(), locator, serverContext);
                return projectSourceBinding(
                    row.bindingId(),
                    row.sourceType(),
                    locator,
                    row.sourceId(),
                    row.confirmationStatus(),
                    row.exclusionReason(),
                    row.sourceVersion(),
                    checkedAt,
                    resolution,
                    allowActions
                );
            })
            .toList();
        return WarehousePlanContract.evaluateSourceInventory(
            bindings,
            snapshot.plan().onboardingMode(),
            snapshot.version(),
            checkedAt
        );
    }

    private SourceInventorySnapshot loadSourceInventorySnapshot(String tenantId, UUID planId) {
        return loadSourceInventorySnapshot(tenantId, planId, get(tenantId, planId));
    }

    private SourceInventorySnapshot loadSourceInventorySnapshot(
        String tenantId,
        UUID planId,
        WarehousePlanHeader plan
    ) {
        return new SourceInventorySnapshot(
            plan,
            loadSourceRows(tenantId, planId),
            readEditUnitVersion(tenantId, planId, EditUnit.SOURCES)
        );
    }

    private PagedSourceInventorySnapshot loadPagedSourceInventorySnapshot(
        String tenantId,
        UUID planId,
        int page,
        int size
    ) {
        WarehousePlanHeader plan = get(tenantId, planId);
        long totalElements = Optional
            .ofNullable(
                jdbcTemplate.queryForObject(
                    "select count(*) from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
                    Long.class,
                    tenantId,
                    planId
                )
            )
            .orElse(0L);
        List<SourceRow> rows = jdbcTemplate.query(
            """
            select id, source_type, source_id, source_version, locator_json::text as locator_json,
                   confirmation_status, exclusion_reason, resolution_status, last_validated_at
              from modeling_warehouse_plan_source
             where tenant_id = ? and plan_id = ?
             order by source_type, source_id, id
             limit ? offset ?
            """,
            WarehousePlanApplicationService::mapSourceRow,
            tenantId,
            planId,
            size,
            (long) page * size
        );
        return new PagedSourceInventorySnapshot(
            new SourceInventorySnapshot(plan, rows, readEditUnitVersion(tenantId, planId, EditUnit.SOURCES)),
            totalElements
        );
    }

    private List<SourceRow> loadSourceRows(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            """
            select id, source_type, source_id, source_version, locator_json::text as locator_json,
                   confirmation_status, exclusion_reason, resolution_status, last_validated_at
              from modeling_warehouse_plan_source
             where tenant_id = ? and plan_id = ?
             order by source_type, source_id, id
            """,
            WarehousePlanApplicationService::mapSourceRow,
            tenantId,
            planId
        );
    }

    private static SourceRow mapSourceRow(ResultSet row, int rowNumber) throws SQLException {
        return new SourceRow(
            row.getObject("id", UUID.class),
            SourceType.valueOf(row.getString("source_type")),
            row.getString("source_id"),
            row.getString("source_version"),
            row.getString("locator_json"),
            ConfirmationStatus.valueOf(row.getString("confirmation_status")),
            row.getString("exclusion_reason"),
            row.getString("resolution_status"),
            row.getTimestamp("last_validated_at") == null ? null : row.getTimestamp("last_validated_at").toInstant()
        );
    }

    private SourceBindingView toSourceBindingView(ResolvedSourceWrite write, boolean allowActions) {
        return projectSourceBinding(
            write.bindingId(),
            write.sourceType(),
            write.locator(),
            write.sourceId(),
            write.confirmationStatus(),
            write.exclusionReason(),
            write.confirmedVersion(),
            write.checkedAt(),
            write.resolution(),
            allowActions
        );
    }

    private SourceBindingView projectSourceBinding(
        UUID bindingId,
        SourceType sourceType,
        SourceLocator locator,
        String sourceId,
        ConfirmationStatus confirmationStatus,
        String exclusionReason,
        String confirmedVersion,
        Instant checkedAt,
        SourceReferenceResolver.ResolvedSource resolution
    ) {
        return projectSourceBinding(
            bindingId,
            sourceType,
            locator,
            sourceId,
            confirmationStatus,
            exclusionReason,
            confirmedVersion,
            checkedAt,
            resolution,
            false
        );
    }

    private SourceBindingView projectSourceBinding(
        UUID bindingId,
        SourceType sourceType,
        SourceLocator locator,
        String sourceId,
        ConfirmationStatus confirmationStatus,
        String exclusionReason,
        String confirmedVersion,
        Instant checkedAt,
        SourceReferenceResolver.ResolvedSource resolution,
        boolean allowActions
    ) {
        boolean identityVisible = resolution.status() == SourceReferenceResolver.ResolutionStatus.AVAILABLE;
        SourceFreshness sourceFreshness = freshness(confirmedVersion, resolution);
        SourceDriftEvidence drift = identityVisible
            ? sourceDriftEvidence(sourceType, locator, sourceFreshness)
            : SourceDriftEvidence.none();
        String reasonCode = sourceReasonCode(confirmationStatus, resolution.status(), sourceFreshness, drift.impact());
        return new SourceBindingView(
            bindingId,
            sourceType,
            identityVisible ? locator : null,
            identityVisible ? sourceId : null,
            confirmationStatus,
            exclusionReason,
            identityVisible ? resolution.displayName() : null,
            identityVisible ? confirmedVersion : null,
            identityVisible ? resolution.resolvedVersion() : null,
            resolution.status(),
            sourceFreshness,
            checkedAt,
            identityVisible ? resolution.resolvedVersion() : null,
            drift.impact(),
            drift.summary(),
            drift.changes(),
            allowedSourceActions(allowActions, confirmationStatus, resolution.status(), sourceFreshness, drift.impact()),
            reasonCode,
            sourceStatusSummary(reasonCode)
        );
    }

    private SourceDriftEvidence sourceDriftEvidence(
        SourceType sourceType,
        SourceLocator locator,
        SourceFreshness sourceFreshness
    ) {
        if (sourceFreshness != SourceFreshness.STALE) {
            return SourceDriftEvidence.none();
        }
        Optional<UUID> datasetId = catalogDatasetId(sourceType, locator);
        if (datasetId.isEmpty()) {
            return SourceDriftEvidence.reviewRequired();
        }
        List<SourceDriftRow> driftRows = jdbcTemplate.query(
            """
            select id,
                   coalesce(added_count, 0) as added_count,
                   coalesce(removed_count, 0) as removed_count,
                   coalesce(changed_count, 0) as changed_count,
                   details_json
              from catalog_schema_drift_event
             where dataset_id = ?
               and coalesce(ticket_status, 'OPEN') in ('OPEN', 'IN_REVIEW')
             order by created_date asc nulls first, id
            """,
            (row, rowNumber) ->
                new SourceDriftRow(
                    row.getObject("id", UUID.class),
                    row.getInt("added_count"),
                    row.getInt("removed_count"),
                    row.getInt("changed_count"),
                    row.getString("details_json")
                ),
            datasetId.orElseThrow()
        );
        if (driftRows.isEmpty()) {
            return SourceDriftEvidence.reviewRequired();
        }
        int added = 0;
        int removed = 0;
        int changed = 0;
        SourceChangeImpact impact = SourceChangeImpact.NONE;
        List<SourceSchemaChange> changes = new ArrayList<>();
        List<UUID> eventIds = new ArrayList<>();
        for (SourceDriftRow row : driftRows) {
            added += row.added();
            removed += row.removed();
            changed += row.changed();
            eventIds.add(row.eventId());
            SchemaDriftDetailsReader.SchemaDriftDetails details = schemaDriftDetailsReader.read(row.detailsJson());
            impact = higherSourceChangeImpact(impact, parseSourceChangeImpact(details.impactLevel()));
            changes.addAll(
                details
                    .changes()
                    .stream()
                    .map(change ->
                        new SourceSchemaChange(
                            stringValue(change.get("field")),
                            stringValue(change.get("kind")),
                            change.get("before"),
                            change.get("after"),
                            stringValue(change.get("impact"))
                        )
                    )
                    .toList()
            );
        }
        if (impact == SourceChangeImpact.NONE) {
            impact = SourceChangeImpact.REVIEW_REQUIRED;
        }
        return reassessUnreferencedSourceChanges(
            sourceType,
            locator,
            new SourceDriftEvidence(
            impact,
            new SourceDiffSummary(added, removed, changed),
            changes,
            eventIds
            )
        );
    }

    private SourceDriftEvidence reassessUnreferencedSourceChanges(
        SourceType sourceType,
        SourceLocator locator,
        SourceDriftEvidence evidence
    ) {
        if (
            evidence.impact() == SourceChangeImpact.NONE ||
            evidence.impact() == SourceChangeImpact.COMPATIBLE ||
            evidence.changes().isEmpty() ||
            locator == null ||
            (sourceType != SourceType.CATALOG_TABLE && sourceType != SourceType.CONNECTION_TABLE)
        ) {
            return evidence;
        }
        Optional<Set<String>> referencedFields = driftConsumerReferences.findCurrentReferencedFields(
            sourceType == SourceType.CATALOG_TABLE ? locator.assetId() : null,
            sourceType == SourceType.CONNECTION_TABLE ? locator.connectionId() : null,
            locator.namespace(),
            locator.objectName()
        );
        if (referencedFields.isEmpty()) {
            return evidence;
        }
        Set<String> normalizedReferences = referencedFields
            .orElseThrow()
            .stream()
            .filter(field -> !isBlank(field))
            .map(field -> field.trim().toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        SourceChangeImpact reassessedImpact = SourceChangeImpact.NONE;
        List<SourceSchemaChange> reassessedChanges = new ArrayList<>();
        for (SourceSchemaChange change : evidence.changes()) {
            String field = change.field();
            SourceChangeImpact changeImpact = parseSourceChangeImpact(change.impact());
            if (!isBlank(field) && !normalizedReferences.contains(field.trim().toLowerCase(java.util.Locale.ROOT))) {
                changeImpact = SourceChangeImpact.COMPATIBLE;
            }
            reassessedImpact = higherSourceChangeImpact(reassessedImpact, changeImpact);
            reassessedChanges.add(
                new SourceSchemaChange(change.field(), change.kind(), change.before(), change.after(), changeImpact.name())
            );
        }
        if (reassessedImpact == SourceChangeImpact.NONE) {
            reassessedImpact = evidence.impact();
        }
        return new SourceDriftEvidence(reassessedImpact, evidence.summary(), reassessedChanges, evidence.eventIds());
    }

    private Optional<UUID> catalogDatasetId(SourceType sourceType, SourceLocator locator) {
        if (sourceType == null || locator == null) {
            return Optional.empty();
        }
        List<UUID> ids = switch (sourceType) {
            case CATALOG_TABLE ->
                locator.assetId() == null
                    ? List.of()
                    : jdbcTemplate.query(
                        "select dataset_id from catalog_table_schema where id = ?",
                        (row, rowNumber) -> row.getObject("dataset_id", UUID.class),
                        locator.assetId()
                    );
            case CONNECTION_TABLE ->
                locator.connectionId() == null || isBlank(locator.namespace()) || isBlank(locator.objectName())
                    ? List.of()
                    : jdbcTemplate.query(
                        """
                        select id
                          from catalog_dataset
                         where source_id = ?
                           and lower(hive_database) = lower(?)
                           and lower(hive_table) = lower(?)
                         order by last_modified_date desc nulls last, id
                         limit 1
                        """,
                        (row, rowNumber) -> row.getObject("id", UUID.class),
                        locator.connectionId(),
                        locator.namespace(),
                        locator.objectName()
                    );
            case EXCEL_FILE, DBT_NODE -> List.of();
        };
        return ids.stream().filter(Objects::nonNull).findFirst();
    }

    private static SourceChangeImpact parseSourceChangeImpact(String impactLevel) {
        if (isBlank(impactLevel)) {
            return SourceChangeImpact.REVIEW_REQUIRED;
        }
        try {
            return SourceChangeImpact.valueOf(impactLevel.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SourceChangeImpact.REVIEW_REQUIRED;
        }
    }

    private static SourceChangeImpact higherSourceChangeImpact(
        SourceChangeImpact left,
        SourceChangeImpact right
    ) {
        return sourceChangeImpactRank(left) >= sourceChangeImpactRank(right) ? left : right;
    }

    private static int sourceChangeImpactRank(SourceChangeImpact impact) {
        return switch (impact) {
            case NONE -> 0;
            case COMPATIBLE -> 1;
            case REVIEW_REQUIRED -> 2;
            case BREAKING -> 3;
        };
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<SourceAction> allowedSourceActions(
        boolean allowActions,
        ConfirmationStatus confirmationStatus,
        SourceReferenceResolver.ResolutionStatus resolutionStatus,
        SourceFreshness sourceFreshness,
        SourceChangeImpact changeImpact
    ) {
        if (!allowActions || confirmationStatus == ConfirmationStatus.EXCLUDED) {
            return List.of();
        }
        if (resolutionStatus != SourceReferenceResolver.ResolutionStatus.AVAILABLE) {
            return List.of(SourceAction.EXCLUDE);
        }
        if (confirmationStatus == ConfirmationStatus.CANDIDATE) {
            return List.of(SourceAction.CONFIRM, SourceAction.EXCLUDE);
        }
        if (sourceFreshness == SourceFreshness.STALE && changeImpact == SourceChangeImpact.COMPATIBLE) {
            return List.of(SourceAction.RECONFIRM, SourceAction.EXCLUDE);
        }
        return List.of(SourceAction.EXCLUDE);
    }

    private static String sourceReasonCode(
        ConfirmationStatus confirmationStatus,
        SourceReferenceResolver.ResolutionStatus resolutionStatus,
        SourceFreshness sourceFreshness,
        SourceChangeImpact changeImpact
    ) {
        if (confirmationStatus == ConfirmationStatus.EXCLUDED) {
            return "SOURCE_EXCLUDED";
        }
        return switch (resolutionStatus) {
            case MISSING -> "SOURCE_MISSING";
            case FORBIDDEN -> "SOURCE_FORBIDDEN";
            case PROVIDER_ERROR -> "SOURCE_PROVIDER_ERROR";
            case AVAILABLE -> {
                if (sourceFreshness == SourceFreshness.CURRENT) {
                    yield "SOURCE_CURRENT";
                }
                yield switch (changeImpact) {
                    case COMPATIBLE -> "SOURCE_DRIFT_COMPATIBLE";
                    case BREAKING -> "SOURCE_DRIFT_BREAKING";
                    case NONE, REVIEW_REQUIRED -> "SOURCE_DRIFT_REVIEW_REQUIRED";
                };
            }
        };
    }

    private static String sourceStatusSummary(String reasonCode) {
        return switch (reasonCode) {
            case "SOURCE_CURRENT" -> "来源结构与已确认版本一致";
            case "SOURCE_DRIFT_COMPATIBLE" -> "来源结构已更新，现有模型可继续使用；确认后可采用新版本";
            case "SOURCE_DRIFT_BREAKING" -> "来源结构变更影响现有模型，请先修复字段映射";
            case "SOURCE_DRIFT_REVIEW_REQUIRED" -> "来源结构已更新，需要确认变化影响";
            case "SOURCE_MISSING" -> "来源当前不可用，请恢复采集或排除该来源";
            case "SOURCE_FORBIDDEN" -> "当前账号无权查看此来源";
            case "SOURCE_PROVIDER_ERROR" -> "暂时无法校验来源状态，请稍后重试";
            case "SOURCE_EXCLUDED" -> "该来源已排除，不参与后续建模";
            default -> "来源状态待确认";
        };
    }

    private void closeSourceDriftTickets(List<UUID> eventIds, String actorId) {
        if (eventIds.isEmpty()) {
            return;
        }
        String handledBy = isBlank(actorId) ? "_system" : actorId.trim();
        if (handledBy.length() > 50) {
            handledBy = handledBy.substring(0, 50);
        }
        for (UUID eventId : eventIds) {
            jdbcTemplate.update(
                """
                update catalog_schema_drift_event
                   set ticket_status = 'RESOLVED', handled_at = current_timestamp, handled_by = ?,
                       last_modified_by = ?, last_modified_date = current_timestamp
                 where id = ?
                   and coalesce(ticket_status, 'OPEN') in ('OPEN', 'IN_REVIEW')
                """,
                handledBy,
                handledBy,
                eventId
            );
        }
    }

    private String sourceAssetKey(ResolvedSourceWrite write) {
        Optional<UUID> datasetId = catalogDatasetId(write.sourceType(), write.locator());
        if (datasetId.isPresent()) {
            Optional<String> catalogKey = jdbcTemplate
                .query(
                    """
                    select source_id, hive_database, hive_table, name
                      from catalog_dataset
                     where id = ?
                    """,
                    (row, rowNumber) ->
                        CatalogAssetKey.dataset(
                            row.getObject("source_id", UUID.class),
                            row.getString("hive_database"),
                            row.getString("hive_database"),
                            row.getString("hive_table"),
                            row.getString("name")
                        ),
                    datasetId.orElseThrow()
                )
                .stream()
                .findFirst();
            if (catalogKey.isPresent()) {
                return catalogKey.orElseThrow();
            }
        }
        if (write.sourceType() == SourceType.DBT_NODE && write.locator() != null) {
            return CatalogAssetKey.dbtModel(write.locator().uniqueId(), write.sourceId());
        }
        return write.sourceType().name().toLowerCase(java.util.Locale.ROOT) + ":" + write.sourceId();
    }

    private static void validateSourcePage(int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_SOURCE_PAGE_INVALID",
                "Source inventory page must be non-negative and size must be between 1 and 200",
                null,
                EditUnit.SOURCES
            );
        }
    }

    private static WarehousePlanException sourceVersionChanged() {
        return new WarehousePlanException(
            "WAREHOUSE_PLAN_SOURCE_VERSION_CHANGED",
            "The source changed after it was reviewed; reload the source inventory before reconfirming",
            null,
            EditUnit.SOURCES
        );
    }

    private SourceReferenceResolver.ResolvedSource resolveSource(
        SourceType sourceType,
        SourceLocator locator,
        SourceReferenceResolver.AccessContext accessContext
    ) {
        try {
            SourceReferenceResolver.ResolvedSource result = sourceReferenceResolver.resolve(sourceType, locator, accessContext);
            if (result == null || result.status() == null) {
                return SourceReferenceResolver.ResolvedSource.providerError();
            }
            if (
                result.status() == SourceReferenceResolver.ResolutionStatus.AVAILABLE &&
                !isBlank(result.resolvedVersion())
            ) {
                return result;
            }
            return switch (result.status()) {
                case AVAILABLE, PROVIDER_ERROR -> SourceReferenceResolver.ResolvedSource.providerError();
                case MISSING -> SourceReferenceResolver.ResolvedSource.missing();
                case FORBIDDEN -> SourceReferenceResolver.ResolvedSource.forbidden();
            };
        } catch (RuntimeException exception) {
            return SourceReferenceResolver.ResolvedSource.providerError();
        }
    }

    private SourceLocator readLocator(SourceRow row) {
        if (!isBlank(row.locatorJson())) {
            try {
                return objectMapper.readValue(row.locatorJson(), SourceLocator.class);
            } catch (JsonProcessingException exception) {
                return null;
            }
        }
        try {
            return switch (row.sourceType()) {
                case CATALOG_TABLE -> new SourceLocator(UUID.fromString(row.sourceId()), null, null, null, null, null, null);
                case EXCEL_FILE -> new SourceLocator(null, UUID.fromString(row.sourceId()), null, null, null, null, null);
                case DBT_NODE -> legacyDbtLocator(row.sourceId());
                case CONNECTION_TABLE -> null;
            };
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static SourceLocator legacyDbtLocator(String sourceId) {
        int separator = sourceId == null ? -1 : sourceId.indexOf(':');
        if (separator <= 0 || separator == sourceId.length() - 1) {
            return null;
        }
        return new SourceLocator(null, null, sourceId.substring(0, separator), sourceId.substring(separator + 1), null, null, null);
    }

    private String writeLocator(SourceLocator locator) {
        try {
            return objectMapper.writeValueAsString(locator);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Source locator could not be serialized", exception);
        }
    }

    private static SourceFreshness freshness(
        String confirmedVersion,
        SourceReferenceResolver.ResolvedSource resolution
    ) {
        return switch (resolution.status()) {
            case MISSING -> SourceFreshness.STALE;
            case FORBIDDEN, PROVIDER_ERROR -> SourceFreshness.UNKNOWN;
            case AVAILABLE -> Objects.equals(confirmedVersion, resolution.resolvedVersion())
                ? SourceFreshness.CURRENT
                : SourceFreshness.STALE;
        };
    }

    private static SourceReferenceResolver.AccessContext serverAccessContext(
        String tenantId,
        SourceReferenceResolver.AccessContext requestContext
    ) {
        return new SourceReferenceResolver.AccessContext(
            tenantId,
            requestContext == null ? null : requestContext.actorId(),
            requestContext == null ? null : requestContext.actorDepartmentId()
        );
    }

    private static String sourceKey(SourceType sourceType, String sourceId) {
        return sourceType.name() + "\u0000" + sourceId;
    }

    private static WarehousePlanException invalidSourceInventory(String code, String message) {
        return new WarehousePlanException(
            "WAREHOUSE_PLAN_SOURCE_INVENTORY_INVALID",
            code + ": " + message,
            null,
            EditUnit.SOURCES
        );
    }

    private List<SourceBinding> loadSources(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            """
            select id, source_type, source_id, source_version, confirmation_status, exclusion_reason
              from modeling_warehouse_plan_source
             where tenant_id = ? and plan_id = ?
             order by source_type, source_id, id
            """,
            (row, rowNumber) ->
                new SourceBinding(
                    row.getObject("id", UUID.class),
                    SourceType.valueOf(row.getString("source_type")),
                    row.getString("source_id"),
                    row.getString("source_version"),
                    ConfirmationStatus.valueOf(row.getString("confirmation_status")),
                    row.getString("exclusion_reason")
                ),
            tenantId,
            planId
        );
    }

    private ExistingCreate findCreateByIdempotencyKey(String tenantId, String idempotencyKey) {
        return jdbcTemplate
            .query(
                """
                select id, idempotency_request_hash, idempotency_response_snapshot::text as idempotency_response_snapshot
                  from modeling_warehouse_plan
                 where tenant_id = ? and idempotency_key = ?
                """,
                (row, rowNumber) ->
                    new ExistingCreate(
                        row.getObject("id", UUID.class),
                        row.getString("idempotency_request_hash"),
                        row.getString("idempotency_response_snapshot")
                    ),
                tenantId,
                idempotencyKey
            )
            .stream()
            .findFirst()
            .orElseThrow(() ->
                new WarehousePlanException(
                    "WAREHOUSE_PLAN_IDEMPOTENCY_STATE_INVALID",
                    "The idempotent warehouse plan could not be reloaded",
                    null
                )
            );
    }

    private void persistCreateResultSnapshot(String tenantId, UUID planId, CreateWarehousePlanResult result) {
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Warehouse plan create result could not be serialized", exception);
        }
        int updated = jdbcTemplate.update(
            "update modeling_warehouse_plan set idempotency_response_snapshot = cast(? as jsonb) where tenant_id = ? and id = ?",
            snapshot,
            tenantId,
            planId
        );
        if (updated != 1) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_IDEMPOTENCY_STATE_INVALID",
                "The idempotent warehouse plan snapshot could not be stored",
                null
            );
        }
    }

    private CreateWarehousePlanResult replayCreateResult(ExistingCreate existing) {
        if (existing.responseSnapshot() == null) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_IDEMPOTENCY_STATE_INVALID",
                "The idempotent warehouse plan snapshot is missing",
                null
            );
        }
        try {
            CreateWarehousePlanResult snapshot = objectMapper.readValue(
                existing.responseSnapshot(),
                CreateWarehousePlanResult.class
            );
            if (!existing.planId().equals(snapshot.planId())) {
                throw new WarehousePlanException(
                    "WAREHOUSE_PLAN_IDEMPOTENCY_STATE_INVALID",
                    "The idempotent warehouse plan snapshot does not match its plan",
                    null
                );
            }
            return new CreateWarehousePlanResult(
                snapshot.planId(),
                snapshot.plan(),
                snapshot.version(),
                snapshot.etag(),
                snapshot.initialSourceBindings(),
                snapshot.nextAction(),
                true
            );
        } catch (JsonProcessingException exception) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_IDEMPOTENCY_STATE_INVALID",
                "The idempotent warehouse plan snapshot is unreadable",
                null
            );
        }
    }

    private static String createRequestHash(CreateWarehousePlanCommand command) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, command.name());
        appendCanonical(canonical, command.objective());
        appendCanonical(canonical, command.scope());
        appendCanonical(canonical, command.onboardingMode().name());
        appendCanonical(canonical, command.ownerId());
        appendCanonical(canonical, command.ownerDepartmentId());
        for (InitialSourceRef source : sortedInitialSourceRefs(command.initialSourceRefs())) {
            appendCanonical(canonical, source.sourceType().name());
            appendCanonical(canonical, source.sourceId());
            appendCanonical(canonical, source.sourceVersion());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static List<InitialSourceRef> sortedInitialSourceRefs(List<InitialSourceRef> sources) {
        List<InitialSourceRef> sorted = new ArrayList<>(sources == null ? List.of() : sources);
        sorted.sort(
            Comparator.comparing((InitialSourceRef source) -> source.sourceType().name())
                .thenComparing(InitialSourceRef::sourceId)
                .thenComparing(source -> source.sourceVersion() == null ? "" : source.sourceVersion())
        );
        return List.copyOf(sorted);
    }

    private static void appendCanonical(StringBuilder target, String value) {
        String normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private List<WarehousePlanHeader> find(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            HEADER_COLUMNS + " where tenant_id = ? and id = ?",
            WarehousePlanApplicationService::mapHeader,
            tenantId,
            planId
        );
    }

    private void resolveWriteFailure(String tenantId, UUID planId, int expectedVersion) {
        WarehousePlanHeader current = find(tenantId, planId).stream().findFirst().orElseThrow(WarehousePlanApplicationService::notFound);
        if (current.version() != expectedVersion) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_VERSION_CONFLICT",
                "Warehouse plan was changed by another operation",
                current.version()
            );
        }
        throw new WarehousePlanException(
            "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT",
            "Warehouse plan lifecycle does not allow this operation",
            current.version()
        );
    }

    private static WarehousePlanHeader mapHeader(ResultSet row, int rowNumber) throws SQLException {
        return new WarehousePlanHeader(
            row.getObject("id", UUID.class),
            row.getString("tenant_id"),
            row.getString("code"),
            row.getString("name"),
            row.getString("objective"),
            row.getString("scope"),
            row.getString("owner_id"),
            row.getString("owner_department_id"),
            OnboardingMode.valueOf(row.getString("onboarding_mode")),
            LifecycleStatus.valueOf(row.getString("lifecycle_status")),
            row.getInt("version")
        );
    }

    private static void requireServerTenant(String serverTenantId) {
        if (serverTenantId == null || serverTenantId.isBlank()) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_SERVER_TENANT_REQUIRED",
                "Server tenant context is required",
                null
            );
        }
    }

    private static WarehousePlanException notFound() {
        return new WarehousePlanException(
            "WAREHOUSE_PLAN_NOT_FOUND",
            "Warehouse plan does not exist in the current tenant",
            null
        );
    }

    private static void requireExpectedEditVersion(int observedVersion, int expectedVersion, EditUnit editUnit) {
        if (observedVersion != expectedVersion) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT",
                "Warehouse plan edit unit was changed by another operation",
                observedVersion,
                editUnit
            );
        }
    }

    public record UpdatePlanHeaderCommand(
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId
    ) {}

    private record ExistingCreate(UUID planId, String requestHash, String responseSnapshot) {}

    private record CategoryScopeSnapshot(List<DomainBinding> bindings, int version) {
        private CategoryScopeSnapshot {
            bindings = List.copyOf(bindings);
        }
    }

    private record SourceInventorySnapshot(WarehousePlanHeader plan, List<SourceRow> rows, int version) {
        private SourceInventorySnapshot {
            rows = List.copyOf(rows);
        }
    }

    private record PagedSourceInventorySnapshot(SourceInventorySnapshot inventory, long totalElements) {}

    private record PreparedSourceInventory(
        List<ResolvedSourceWrite> writes,
        Instant checkedAt,
        String actorId,
        String correlationId
    ) {
        private PreparedSourceInventory {
            writes = List.copyOf(writes);
        }

        private boolean allReconfirmationsReplayed() {
            boolean hasReplayedReconfirmation = writes
                .stream()
                .anyMatch(write -> write.action() == SourceAction.RECONFIRM && write.reconfirmationReplayed());
            return hasReplayedReconfirmation && writes.stream().allMatch(ResolvedSourceWrite::stateAlreadyApplied);
        }
    }

    private record BaselineSnapshot(
        WarehousePlanHeader plan,
        List<DomainBinding> domainBindings,
        int categoryScopeVersion,
        SourceInventorySnapshot sourceInventory,
        PlanningPolicyCommand policy,
        int policyVersion
    ) {
        private BaselineSnapshot {
            domainBindings = List.copyOf(domainBindings);
        }
    }

    private record SourceRow(
        UUID bindingId,
        SourceType sourceType,
        String sourceId,
        String sourceVersion,
        String locatorJson,
        ConfirmationStatus confirmationStatus,
        String exclusionReason,
        String resolutionStatus,
        Instant lastValidatedAt
    ) {}

    private record ResolvedSourceWrite(
        UUID bindingId,
        SourceType sourceType,
        SourceLocator locator,
        String sourceId,
        ConfirmationStatus confirmationStatus,
        String exclusionReason,
        String confirmedVersion,
        SourceReferenceResolver.ResolvedSource resolution,
        Instant checkedAt,
        SourceAction action,
        String previousConfirmedVersion,
        boolean reconfirmationReplayed,
        boolean stateAlreadyApplied
    ) {}

    private record SourceDriftRow(UUID eventId, int added, int removed, int changed, String detailsJson) {}

    private record SourceDriftEvidence(
        SourceChangeImpact impact,
        SourceDiffSummary summary,
        List<SourceSchemaChange> changes,
        List<UUID> eventIds
    ) {
        private SourceDriftEvidence {
            changes = List.copyOf(changes);
            eventIds = List.copyOf(eventIds);
        }

        private static SourceDriftEvidence none() {
            return new SourceDriftEvidence(SourceChangeImpact.NONE, SourceDiffSummary.empty(), List.of(), List.of());
        }

        private static SourceDriftEvidence reviewRequired() {
            return new SourceDriftEvidence(
                SourceChangeImpact.REVIEW_REQUIRED,
                SourceDiffSummary.empty(),
                List.of(),
                List.of()
            );
        }
    }

    public static final class WarehousePlanException extends RuntimeException {

        private final String code;
        private final Integer currentVersion;
        private final EditUnit editUnit;

        public WarehousePlanException(String code, String message, Integer currentVersion) {
            this(code, message, currentVersion, null);
        }

        public WarehousePlanException(String code, String message, Integer currentVersion, EditUnit editUnit) {
            super(message);
            this.code = code;
            this.currentVersion = currentVersion;
            this.editUnit = editUnit;
        }

        public String code() {
            return code;
        }

        public Integer currentVersion() {
            return currentVersion;
        }

        public EditUnit editUnit() {
            return editUnit;
        }
    }
}
