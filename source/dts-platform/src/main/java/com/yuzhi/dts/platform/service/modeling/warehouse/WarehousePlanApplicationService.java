package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehousePlanApplicationService {

    private static final String HEADER_COLUMNS = """
        select id, tenant_id, code, name, objective, scope, owner_id, owner_department_id,
               onboarding_mode, lifecycle_status, version
          from modeling_warehouse_plan
        """;

    private final JdbcTemplate jdbcTemplate;

    public WarehousePlanApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public WarehousePlanHeader create(String serverTenantId, CreateWarehousePlanCommand command) {
        requireServerTenant(serverTenantId);
        List<DomainIssue> issues = WarehousePlanContract.validateCreate(command);
        if (!issues.isEmpty()) {
            DomainIssue issue = issues.getFirst();
            throw new WarehousePlanException(issue.code(), issue.message(), null);
        }

        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan (
                    id, tenant_id, owner, code, name, objective, scope, owner_id, owner_department_id,
                    onboarding_mode, lifecycle_status, status, version, business_scope_version,
                    sources_version, source_mappings_version, policy_version, created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, 1, 1, current_timestamp, current_timestamp)
                """,
                id,
                serverTenantId,
                command.ownerId(),
                command.code(),
                command.name(),
                command.objective(),
                command.scope(),
                command.ownerId(),
                command.ownerDepartmentId(),
                command.onboardingMode().name(),
                LifecycleStatus.DRAFT.name(),
                LifecycleStatus.DRAFT.name()
            );
        } catch (DataIntegrityViolationException exception) {
            if (isConstraintViolation(exception, "uk_modeling_warehouse_plan_tenant_code")) {
                throw new WarehousePlanException(
                    "WAREHOUSE_PLAN_CODE_CONFLICT",
                    "Warehouse plan code already exists in the current tenant",
                    null
                );
            }
            throw exception;
        }
        return get(serverTenantId, id);
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
        return get(serverTenantId, planId);
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
        return get(serverTenantId, planId);
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
        return new Versioned<>(value, expectedVersion + 1);
    }

    @Transactional
    public Versioned<List<SourceBinding>> saveSources(
        String serverTenantId,
        UUID planId,
        int expectedVersion,
        List<SourceBinding> value
    ) {
        requireServerTenant(serverTenantId);
        List<SourceBinding> sources = value == null ? List.of() : List.copyOf(value);
        validateSources(sources);
        casEditUnit(serverTenantId, planId, expectedVersion, EditUnit.SOURCES);

        Set<UUID> incomingIds = new HashSet<>();
        sources.forEach(source -> incomingIds.add(source.id()));
        List<UUID> existingIds = jdbcTemplate.query(
            "select id from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
            (row, rowNumber) -> row.getObject("id", UUID.class),
            serverTenantId,
            planId
        );
        for (UUID existingId : existingIds) {
            if (incomingIds.contains(existingId)) {
                continue;
            }
            Integer mappingCount = jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan_source_mapping where tenant_id = ? and plan_id = ? and source_binding_id = ?",
                Integer.class,
                serverTenantId,
                planId,
                existingId
            );
            if (mappingCount != null && mappingCount > 0) {
                throw new WarehousePlanException(
                    "WAREHOUSE_PLAN_SOURCE_IN_USE",
                    "A mapped source must be removed from source mappings before it can be removed from inventory",
                    expectedVersion + 1,
                    EditUnit.SOURCES
                );
            }
            jdbcTemplate.update(
                "delete from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ? and id = ?",
                serverTenantId,
                planId,
                existingId
            );
        }
        for (SourceBinding source : sources) {
            int updated = jdbcTemplate.update(
                """
                update modeling_warehouse_plan_source
                   set source_type = ?, source_id = ?, source_version = ?, confirmation_status = ?, exclusion_reason = ?,
                       last_modified_date = current_timestamp
                 where tenant_id = ? and plan_id = ? and id = ?
                """,
                source.sourceType().name(),
                source.sourceId(),
                source.sourceVersion(),
                source.confirmationStatus().name(),
                source.exclusionReason(),
                serverTenantId,
                planId,
                source.id()
            );
            if (updated == 0) {
                jdbcTemplate.update(
                    """
                    insert into modeling_warehouse_plan_source
                        (id, tenant_id, plan_id, source_type, source_id, source_version, confirmation_status,
                         exclusion_reason, created_date, last_modified_date)
                    values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                    """,
                    source.id(),
                    serverTenantId,
                    planId,
                    source.sourceType().name(),
                    source.sourceId(),
                    source.sourceVersion(),
                    source.confirmationStatus().name(),
                    source.exclusionReason()
                );
            }
        }
        return new Versioned<>(sources, expectedVersion + 1);
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
        return new Versioned<>(mappings, expectedVersion + 1);
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
                     created_date, last_modified_date)
                values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
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

    @Transactional(readOnly = true)
    public PlanningBaseline getBaseline(String serverTenantId, UUID planId) {
        requireServerTenant(serverTenantId);
        get(serverTenantId, planId);
        return loadBaseline(serverTenantId, planId);
    }

    @Transactional
    public PlanningBaseline confirmBaseline(String serverTenantId, UUID planId, int expectedPlanHeadVersion) {
        requireServerTenant(serverTenantId);
        WarehousePlanHeader current = lockPlan(serverTenantId, planId);
        if (current.version() != expectedPlanHeadVersion) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_VERSION_CONFLICT",
                "Warehouse plan was changed by another operation",
                current.version()
            );
        }
        PlanningBaseline baseline = loadBaseline(serverTenantId, planId);
        if (!baseline.ready()) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_BASELINE_INCOMPLETE",
                "Warehouse planning baseline is incomplete: " + String.join(",", baseline.missingCodes()),
                null
            );
        }
        if (!WarehousePlanContract.canTransition(current.lifecycleStatus(), LifecycleStatus.BASELINE_READY)) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT",
                "Warehouse plan lifecycle does not allow baseline confirmation",
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

    private static void validateSources(List<SourceBinding> sources) {
        Set<UUID> ids = new HashSet<>();
        Set<String> references = new HashSet<>();
        for (SourceBinding source : sources) {
            if (
                source == null ||
                source.id() == null ||
                source.sourceType() == null ||
                isBlank(source.sourceId()) ||
                source.confirmationStatus() == null ||
                !ids.add(source.id()) ||
                !references.add(source.sourceType().name() + "\u0000" + source.sourceId())
            ) {
                throw invalidEditUnit(EditUnit.SOURCES, "Sources must be unique and complete");
            }
            if (source.confirmationStatus() == ConfirmationStatus.EXCLUDED && isBlank(source.exclusionReason())) {
                throw invalidEditUnit(EditUnit.SOURCES, "Excluded sources require a reason");
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
        if (current.lifecycleStatus() == LifecycleStatus.PUBLISHED || current.lifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT",
                "Warehouse plan lifecycle does not allow this operation",
                expectedVersion,
                editUnit
            );
        }
        Integer currentVersion = jdbcTemplate.queryForObject(
            "select " + versionColumn(editUnit) + " from modeling_warehouse_plan where tenant_id = ? and id = ?",
            Integer.class,
            tenantId,
            planId
        );
        throw new WarehousePlanException(
            "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT",
            "Warehouse plan edit unit was changed by another operation",
            currentVersion,
            editUnit
        );
    }

    private PlanningBaseline loadBaseline(String tenantId, UUID planId) {
        Boolean scopeConfirmed = jdbcTemplate.queryForObject(
            "select business_scope_confirmed from modeling_warehouse_plan where tenant_id = ? and id = ?",
            Boolean.class,
            tenantId,
            planId
        );
        List<DomainBinding> domains = jdbcTemplate.query(
            "select domain_id, confirmation_status from modeling_warehouse_plan_domain where tenant_id = ? and plan_id = ? order by id",
            (row, rowNumber) ->
                new DomainBinding(
                    row.getObject("domain_id", UUID.class),
                    ConfirmationStatus.valueOf(row.getString("confirmation_status"))
                ),
            tenantId,
            planId
        );
        List<ProcessBinding> processes = jdbcTemplate.query(
            "select process_id, domain_id, process_shape, confirmation_status from modeling_warehouse_plan_process where tenant_id = ? and plan_id = ? order by id",
            (row, rowNumber) ->
                new ProcessBinding(
                    row.getString("process_id"),
                    row.getObject("domain_id", UUID.class),
                    row.getString("process_shape"),
                    ConfirmationStatus.valueOf(row.getString("confirmation_status"))
                ),
            tenantId,
            planId
        );
        List<MetricRequirement> metrics = jdbcTemplate.query(
            """
            select id, metric_ref, name, definition, domain_id, process_id, confirmation_status
              from modeling_warehouse_plan_metric_need
             where tenant_id = ? and plan_id = ?
             order by id
            """,
            (row, rowNumber) ->
                new MetricRequirement(
                    row.getObject("id", UUID.class),
                    row.getString("metric_ref"),
                    row.getString("name"),
                    row.getString("definition"),
                    row.getObject("domain_id", UUID.class),
                    row.getString("process_id"),
                    ConfirmationStatus.valueOf(row.getString("confirmation_status"))
                ),
            tenantId,
            planId
        );
        List<SourceBinding> sources = jdbcTemplate.query(
            """
            select id, source_type, source_id, source_version, confirmation_status, exclusion_reason
              from modeling_warehouse_plan_source
             where tenant_id = ? and plan_id = ?
             order by id
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
        List<SourceBusinessMapping> mappings = jdbcTemplate.query(
            """
            select id, source_binding_id, domain_id, process_id, mapping_status, notes
              from modeling_warehouse_plan_source_mapping
             where tenant_id = ? and plan_id = ?
             order by id
            """,
            (row, rowNumber) ->
                new SourceBusinessMapping(
                    row.getObject("id", UUID.class),
                    row.getObject("source_binding_id", UUID.class),
                    row.getObject("domain_id", UUID.class),
                    row.getString("process_id"),
                    ConfirmationStatus.valueOf(row.getString("mapping_status")),
                    row.getString("notes")
                ),
            tenantId,
            planId
        );
        List<PlanningPolicy> policies = jdbcTemplate.query(
            """
            select layer_policy_code, naming_policy_ref, history_policy, default_time_zone
              from modeling_warehouse_plan_policy
             where tenant_id = ? and plan_id = ?
            """,
            (row, rowNumber) ->
                new PlanningPolicy(
                    row.getString("layer_policy_code"),
                    row.getString("naming_policy_ref"),
                    row.getString("history_policy"),
                    row.getString("default_time_zone")
                ),
            tenantId,
            planId
        );
        BusinessScope businessScope = new BusinessScope(
            Boolean.TRUE.equals(scopeConfirmed),
            domains,
            processes,
            metrics
        );
        return WarehousePlanContract.evaluateBaseline(businessScope, sources, mappings, policies.isEmpty() ? null : policies.getFirst());
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
            case BUSINESS_SCOPE -> "business_scope_version";
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static WarehousePlanException invalidEditUnit(EditUnit editUnit, String message) {
        return new WarehousePlanException("WAREHOUSE_PLAN_EDIT_UNIT_INVALID", message, null, editUnit);
    }

    private List<WarehousePlanHeader> find(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            HEADER_COLUMNS + " where tenant_id = ? and id = ?",
            WarehousePlanApplicationService::mapHeader,
            tenantId,
            planId
        );
    }

    private static boolean isConstraintViolation(DataIntegrityViolationException exception, String constraintName) {
        Throwable cause = exception.getMostSpecificCause();
        return cause.getMessage() != null && cause.getMessage().contains(constraintName);
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

    public record UpdatePlanHeaderCommand(
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId
    ) {}

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
