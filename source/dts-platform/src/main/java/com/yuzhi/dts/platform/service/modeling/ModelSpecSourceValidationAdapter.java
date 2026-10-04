package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PhysicalSourceProjection;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Server-authoritative source validation for canonical ModelSpec writes. */
@Component
public class ModelSpecSourceValidationAdapter implements ModelSpecSourceValidationPort {

    private static final Logger LOG = LoggerFactory.getLogger(ModelSpecSourceValidationAdapter.class);

    @org.springframework.beans.factory.annotation.Autowired
    private ModelingSourceScopeGuard sourceScope;
    private final ModelSpecRepository repository;
    private final SourceReferenceResolver resolver;
    private final WarehousePlanActorProvider actorProvider;
    private final ObjectMapper objectMapper;
    private final ModelSpecPlanWriteAccessPort planWriteAccess;

    public ModelSpecSourceValidationAdapter(
        ModelSpecRepository repository,
        SourceReferenceResolver resolver,
        WarehousePlanActorProvider actorProvider,
        ObjectMapper objectMapper,
        ModelSpecPlanWriteAccessPort planWriteAccess
    ) {
        this.repository = repository;
        this.resolver = resolver;
        this.actorProvider = actorProvider;
        this.objectMapper = objectMapper;
        this.planWriteAccess = planWriteAccess;
    }

    @Override
    public boolean isCurrentBinding(String tenantId, UUID planId, String actorId, SourceRef sourceRef) {
        if (isBlank(tenantId) || planId == null || isBlank(actorId) || sourceRef == null || sourceRef.sourceBindingId() == null) {
            return false;
        }

        WarehousePlanActor actor = currentActor();
        if (actor == null || isBlank(actor.ownerId()) || !Objects.equals(actorId, actor.ownerId())) return false;
        // Temporary alignment with the canonical writer until the authorization refactor:
        // authorized maintainers need not own the plan; source access still uses the actual actor.
        if (!planWriteAccess.canMaintain(tenantId, planId, actorId)) return false;

        SourceBindingState binding = repository
            .lockSourceBinding(tenantId, planId, sourceRef.sourceBindingId())
            .orElse(null);

        return isCurrentResolvedBinding(tenantId, actorId, actor.ownerDepartmentId(), sourceRef, binding, false);
    }

    @Override
    public boolean isCurrentBindingForGate(String tenantId, UUID planId, SourceRef sourceRef) {
        if (isBlank(tenantId) || planId == null || sourceRef == null || sourceRef.sourceBindingId() == null) return false;
        try {
            SourceBindingState binding = repository
                .findSourceBinding(tenantId, planId, sourceRef.sourceBindingId())
                .orElse(null);
            if (binding == null) return false;
            return isCurrentResolvedBinding(
                tenantId,
                binding.planOwnerId(),
                binding.planOwnerDepartmentId(),
                sourceRef,
                binding,
                false
            );
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn("ModelSpec gate source validation failed ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean isCurrentBindingForGate(String tenantId, UUID planId, UUID sourceBindingId, String resolvedVersion) {
        if (isBlank(tenantId) || planId == null || sourceBindingId == null || isBlank(resolvedVersion)) return false;
        try {
            SourceBindingState binding = repository.findSourceBinding(tenantId, planId, sourceBindingId).orElse(null);
            if (binding == null) return false;
            SourceKind kind = sourceKind(binding.sourceType());
            if (kind == null) return false;
            SourceRef reference = new SourceRef(
                kind,
                binding.sourceId(),
                null,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                null,
                sourceBindingId,
                resolvedVersion
            );
            return isCurrentResolvedBinding(
                tenantId,
                binding.planOwnerId(),
                binding.planOwnerDepartmentId(),
                reference,
                binding,
                false
            );
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn("Model implementation source validation failed ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean isCurrentBindingForExecution(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    ) {
        if (isBlank(tenantId) || planId == null || sourceBindingId == null || isBlank(resolvedVersion)) return false;
        try {
            SourceBindingState binding = repository.findSourceBinding(tenantId, planId, sourceBindingId).orElse(null);
            if (binding == null) return false;
            SourceKind kind = sourceKind(binding.sourceType());
            if (kind == null) return false;
            SourceRef reference = new SourceRef(
                kind,
                binding.sourceId(),
                null,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                null,
                sourceBindingId,
                resolvedVersion
            );
            return isCurrentResolvedBinding(
                tenantId,
                binding.planOwnerId(),
                binding.planOwnerDepartmentId(),
                reference,
                binding,
                true
            );
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn("Model materialization source validation failed ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public Optional<SourceRef> resolveCurrentBindingForCompiler(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    ) {
        if (!isCurrentBindingForGate(tenantId, planId, sourceBindingId, resolvedVersion)) return Optional.empty();
        try {
            PhysicalSourceProjection source = repository
                .findCurrentPhysicalSource(tenantId, planId, sourceBindingId, resolvedVersion)
                .orElse(null);
            if (source == null) return Optional.empty();
            return Optional.of(
                new SourceRef(
                    source.kind(),
                    source.ref(),
                    source.layer(),
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    sourceBindingId,
                    source.resolvedVersion()
                )
            );
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn("Model implementation compiler source resolution failed ({})", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<SourceRef> resolveCurrentBindingForExecutionCompiler(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    ) {
        if (!isCurrentBindingForExecution(tenantId, planId, sourceBindingId, resolvedVersion)) {
            return Optional.empty();
        }
        try {
            PhysicalSourceProjection source = repository
                .findCurrentPhysicalSource(tenantId, planId, sourceBindingId, resolvedVersion)
                .orElse(null);
            if (source == null) return Optional.empty();
            return Optional.of(
                new SourceRef(
                    source.kind(),
                    source.ref(),
                    source.layer(),
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    sourceBindingId,
                    source.resolvedVersion()
                )
            );
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn("Model execution compiler source resolution failed ({})", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean isCurrentResolvedBinding(
        String tenantId,
        String ownerId,
        String ownerDepartmentId,
        SourceRef sourceRef,
        SourceBindingState binding,
        boolean backgroundExecution
    ) {
        if (
            binding == null ||
            !"CONFIRMED".equals(binding.confirmationStatus()) ||
            !sourceKindMatches(sourceRef.kind(), binding.sourceType()) ||
            !Objects.equals(sourceRef.ref(), binding.sourceId()) ||
            !Objects.equals(sourceRef.resolvedVersion(), binding.sourceVersion())
        ) {
            return false;
        }

        SourceType sourceType = sourceType(binding.sourceType());
        SourceLocator locator = readLocator(binding, sourceType);
        if (sourceType == null || locator == null) return false;
        try {
            if (!Objects.equals(binding.sourceId(), WarehousePlanContract.canonicalSourceId(sourceType, locator))) return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }

        UUID targetPlan = repositoryPlanId(tenantId, binding.id());
        sourceScope.requireSource(tenantId, targetPlan, sourceType, locator);
        var current = com.yuzhi.dts.platform.security.modeling.ModelingIdentity.optional();
        if (current.isEmpty() && !ModelingSystemExecution.permits(tenantId, targetPlan)) return false;
        AccessContext context = current.isPresent()
            ? new AccessContext(tenantId, current.orElseThrow().id(), current.orElseThrow().deptCode())
            : new AccessContext(tenantId, "SYSTEM", ownerDepartmentId);
        backgroundExecution = current.isEmpty();
        ResolvedSource resolved = backgroundExecution
            ? resolveForExecution(sourceType, locator, context)
            : resolve(sourceType, locator, context);
        return resolved != null &&
        resolved.status() == ResolutionStatus.AVAILABLE &&
        !isBlank(resolved.resolvedVersion()) &&
        Objects.equals(binding.sourceVersion(), resolved.resolvedVersion()) &&
        Objects.equals(sourceRef.resolvedVersion(), resolved.resolvedVersion());
    }

    @Override
    public java.util.List<com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort.SourceField> readFields(
        String tenantId, UUID planId, UUID bindingId, String version) {
        WarehousePlanActor actor = currentActor();
        if (actor == null || !isCurrentBindingForGate(tenantId, planId, bindingId, version)) return java.util.List.of();
        SourceBindingState binding = repository.findSourceBinding(tenantId, planId, bindingId).orElse(null);
        if (binding == null) return java.util.List.of();
        SourceType type = sourceType(binding.sourceType());
        SourceLocator locator = readLocator(binding, type);
        if (type == null || locator == null) return java.util.List.of();
        return resolver.readFields(type, locator, new AccessContext(tenantId, actor.ownerId(), actor.ownerDepartmentId()), version);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private UUID repositoryPlanId(String tenant, UUID binding) {
        return jdbc.queryForObject("select plan_id from modeling_warehouse_plan_source where tenant_id=? and id=?", UUID.class, tenant, binding);
    }

    private WarehousePlanActor currentActor() {
        try {
            return actorProvider.currentActor();
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn("ModelSpec source validation could not read the authenticated actor ({})", exception.getClass().getSimpleName());
            return null;
        }
    }

    private ResolvedSource resolve(SourceType sourceType, SourceLocator locator, AccessContext context) {
        try {
            return resolver.resolve(sourceType, locator, context);
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn(
                "ModelSpec live source resolution failed for type {} ({})",
                sourceType,
                exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private ResolvedSource resolveForExecution(
        SourceType sourceType,
        SourceLocator locator,
        AccessContext context
    ) {
        try {
            return resolver.resolveForExecution(sourceType, locator, context);
        } catch (ModelSpecException | com.yuzhi.dts.platform.security.modeling.ModelingIdentityException denied) {
            throw denied;
        } catch (RuntimeException exception) {
            LOG.warn(
                "Model materialization live source resolution failed for type {} ({})",
                sourceType,
                exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private SourceLocator readLocator(SourceBindingState binding, SourceType sourceType) {
        if (!isBlank(binding.locatorJson())) {
            try {
                return objectMapper.readValue(binding.locatorJson(), SourceLocator.class);
            } catch (JsonProcessingException exception) {
                return null;
            }
        }
        if (sourceType == null) return null;
        try {
            return switch (sourceType) {
                case CATALOG_TABLE -> new SourceLocator(UUID.fromString(binding.sourceId()), null, null, null, null, null, null);
                case EXCEL_FILE -> new SourceLocator(null, UUID.fromString(binding.sourceId()), null, null, null, null, null);
                case DBT_NODE -> legacyDbtLocator(binding.sourceId());
                case CONNECTION_TABLE -> null;
            };
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static SourceLocator legacyDbtLocator(String sourceId) {
        int separator = sourceId == null ? -1 : sourceId.indexOf(':');
        if (separator <= 0 || separator == sourceId.length() - 1) return null;
        return new SourceLocator(null, null, sourceId.substring(0, separator), sourceId.substring(separator + 1), null, null, null);
    }

    private static SourceType sourceType(String sourceType) {
        try {
            return SourceType.valueOf(sourceType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private static boolean sourceKindMatches(SourceKind kind, String sourceType) {
        if (kind == null || sourceType == null) return false;
        return switch (sourceType) {
            case "CONNECTION_TABLE", "CATALOG_TABLE" -> kind == SourceKind.TABLE;
            case "EXCEL_FILE" -> kind == SourceKind.DATASET;
            case "DBT_NODE" -> kind == SourceKind.DBT_MODEL;
            default -> false;
        };
    }

    private static SourceKind sourceKind(String sourceType) {
        if (sourceType == null) return null;
        return switch (sourceType) {
            case "CONNECTION_TABLE", "CATALOG_TABLE" -> SourceKind.TABLE;
            case "EXCEL_FILE" -> SourceKind.DATASET;
            case "DBT_NODE" -> SourceKind.DBT_MODEL;
            default -> null;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
