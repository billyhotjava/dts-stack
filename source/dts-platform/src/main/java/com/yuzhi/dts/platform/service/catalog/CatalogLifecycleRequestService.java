package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLifecycleRequestRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.dto.LifecycleRequestDto;
import com.yuzhi.dts.platform.service.catalog.request.LifecycleRequestCreateRequest;
import com.yuzhi.dts.platform.service.catalog.request.LifecycleRequestDecisionRequest;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogLifecycleRequestService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String TYPE_ARCHIVE = "ARCHIVE";
    private static final String TYPE_DISPOSE = "DISPOSE";
    private static final String TYPE_EXTEND = "EXTEND_RETENTION";

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogLifecycleRequestRepository requestRepository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final AuditService auditService;
    private final CatalogLifecycleControlService lifecycleControlService;

    public CatalogLifecycleRequestService(
        CatalogDatasetRepository datasetRepository,
        CatalogLifecycleRequestRepository requestRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        AuditService auditService
    ) {
        this(
            datasetRepository,
            requestRepository,
            accessChecker,
            organizationVisibilityService,
            auditService,
            null
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogLifecycleRequestService(
        CatalogDatasetRepository datasetRepository,
        CatalogLifecycleRequestRepository requestRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        AuditService auditService,
        CatalogLifecycleControlService lifecycleControlService
    ) {
        this.datasetRepository = datasetRepository;
        this.requestRepository = requestRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.auditService = auditService;
        this.lifecycleControlService = lifecycleControlService;
    }

    @Transactional(readOnly = true)
    public List<LifecycleRequestDto> list(String status, UUID datasetId, int limit, String activeDeptHeader) {
        Pageable pageable = PageRequest.of(0, normalizeLimit(limit), Sort.Direction.DESC, "createdDate");

        Specification<CatalogLifecycleRequest> spec = Specification.where(null);
        if (StringUtils.isNotBlank(status)) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("status")), normalized));
        }
        if (datasetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("datasetId"), datasetId));
        }

        List<CatalogLifecycleRequest> requests = requestRepository.findAll(spec, pageable).getContent();
        if (requests.isEmpty()) {
            return List.of();
        }

        List<UUID> datasetIds = requests.stream().map(CatalogLifecycleRequest::getDatasetId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, CatalogDataset> datasetMap = datasetRepository
            .findAllById(datasetIds)
            .stream()
            .collect(Collectors.toMap(CatalogDataset::getId, Function.identity(), (a, b) -> a));

        String activeDept = resolveActiveDept(activeDeptHeader);
        return requests
            .stream()
            .filter(req -> canReadRequest(req, datasetMap.get(req.getDatasetId()), activeDept))
            .map(req -> toDto(req, datasetMap.get(req.getDatasetId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LifecycleRequestDto get(UUID id, String activeDeptHeader) {
        CatalogLifecycleRequest request = requestRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        CatalogDataset dataset = request.getDatasetId() != null ? datasetRepository.findById(request.getDatasetId()).orElse(null) : null;
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (!canReadRequest(request, dataset, activeDept)) {
            throw new AccessDeniedException("无权查看该生命周期申请");
        }
        return toDto(request, dataset);
    }

    public LifecycleRequestDto submit(LifecycleRequestCreateRequest input, String actor, String activeDeptHeader) {
        if (input == null || input.getDatasetId() == null) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }

        CatalogDataset dataset = datasetRepository.findById(input.getDatasetId()).orElseThrow(() -> new EntityNotFoundException("数据集不存在"));
        ensureDatasetAllowed(dataset, activeDeptHeader);

        String requestType = normalizeRequestType(input.getRequestType());
        ensureActionAllowed(dataset, actionForRequestType(requestType));
        String requestedLifecycleStatus = resolveRequestedLifecycleStatus(requestType);
        String pendingLifecycleStatus = resolvePendingLifecycleStatus(requestType);

        CatalogLifecycleRequest request = new CatalogLifecycleRequest();
        request.setDatasetId(dataset.getId());
        request.setOwnerDept(StringUtils.trimToNull(dataset.getOwnerDept()));
        request.setRequestType(requestType);
        request.setStatus(STATUS_PENDING);
        request.setNotes(StringUtils.trimToNull(input.getNotes()));
        request.setPreviousLifecycleStatus(StringUtils.trimToNull(dataset.getLifecycleStatus()));
        request.setRequestedLifecycleStatus(StringUtils.trimToNull(requestedLifecycleStatus));
        request.setPreviousRetentionDays(dataset.getRetentionDays());
        request.setPreviousExpiresAt(dataset.getExpiresAt());
        if (TYPE_EXTEND.equals(requestType)) {
            request.setRequestedRetentionDays(normalizeRetentionDays(input.getRetentionDays()));
            request.setRequestedExpiresAt(input.getExpiresAt());
        }

        requestRepository.save(request);

        if (StringUtils.isNotBlank(pendingLifecycleStatus)) {
            dataset.setLifecycleStatus(pendingLifecycleStatus);
            datasetRepository.save(dataset);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "提交生命周期申请");
        payload.put("datasetId", dataset.getId().toString());
        payload.put("datasetName", dataset.getName());
        payload.put("requestType", requestType);
        payload.put("requestedLifecycleStatus", requestedLifecycleStatus);
        payload.put("ownerDept", dataset.getOwnerDept());
        auditService.auditAction("CATALOG_LIFECYCLE_REQUEST_SUBMIT", AuditStage.SUCCESS, request.getId().toString(), payload);

        return toDto(request, dataset);
    }

    public LifecycleRequestDto decide(UUID id, LifecycleRequestDecisionRequest decision, String actor, String activeDeptHeader) {
        CatalogLifecycleRequest request = requestRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureDecisionAllowed(request, activeDeptHeader);

        if (!STATUS_PENDING.equalsIgnoreCase(StringUtils.trimToEmpty(request.getStatus()))) {
            throw new IllegalStateException("该申请已处理");
        }

        boolean approved = decision != null && Boolean.TRUE.equals(decision.getApproved());
        String notes = decision != null ? StringUtils.trimToNull(decision.getNotes()) : null;
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("system"));
        if (
            approved &&
            StringUtils.equalsIgnoreCase(
                StringUtils.trimToNull(request.getCreatedBy()),
                StringUtils.trimToNull(effectiveActor)
            )
        ) {
            throw new IllegalStateException("申请人不能审批自己的生命周期申请");
        }

        CatalogDataset dataset = request.getDatasetId() != null ? datasetRepository.findById(request.getDatasetId()).orElse(null) : null;
        if (dataset == null) {
            throw new EntityNotFoundException("数据集不存在");
        }
        ensureDatasetAllowed(dataset, activeDeptHeader);

        if (approved) {
            ensureActionAllowed(dataset, actionForRequestType(request.getRequestType()));
            applyApprovedChange(request, dataset, effectiveActor);
            request.setStatus(STATUS_APPROVED);
        } else {
            revertDataset(request, dataset);
            request.setStatus(STATUS_REJECTED);
        }
        request.setDecidedBy(effectiveActor);
        request.setDecidedAt(Instant.now());
        request.setDecisionNotes(notes);
        requestRepository.save(request);
        datasetRepository.save(dataset);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "审批生命周期申请");
        payload.put("requestId", id.toString());
        payload.put("datasetId", dataset.getId().toString());
        payload.put("datasetName", dataset.getName());
        payload.put("requestType", request.getRequestType());
        payload.put("approved", approved);
        auditService.auditAction("CATALOG_LIFECYCLE_REQUEST_DECIDE", AuditStage.SUCCESS, id.toString(), payload);

        return toDto(request, dataset);
    }

    public LifecycleRequestDto cancel(UUID id, String actor, String activeDeptHeader) {
        CatalogLifecycleRequest request = requestRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        String effectiveActor = StringUtils.defaultIfBlank(actor, SecurityUtils.getCurrentUserLogin().orElse("system"));
        if (!Objects.equals(StringUtils.trimToNull(request.getCreatedBy()), StringUtils.trimToNull(effectiveActor)) &&
            !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
            throw new AccessDeniedException("无权撤销该申请");
        }
        if (!STATUS_PENDING.equalsIgnoreCase(StringUtils.trimToEmpty(request.getStatus()))) {
            throw new IllegalStateException("该申请已处理");
        }

        CatalogDataset dataset = request.getDatasetId() != null ? datasetRepository.findById(request.getDatasetId()).orElse(null) : null;
        if (dataset == null) {
            throw new EntityNotFoundException("数据集不存在");
        }
        ensureDatasetAllowed(dataset, activeDeptHeader);

        revertDataset(request, dataset);
        request.setStatus(STATUS_CANCELLED);
        request.setDecidedBy(effectiveActor);
        request.setDecidedAt(Instant.now());
        requestRepository.save(request);
        datasetRepository.save(dataset);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "撤销生命周期申请");
        payload.put("requestId", id.toString());
        payload.put("datasetId", dataset.getId().toString());
        payload.put("datasetName", dataset.getName());
        payload.put("requestType", request.getRequestType());
        auditService.auditAction("CATALOG_LIFECYCLE_REQUEST_CANCEL", AuditStage.SUCCESS, id.toString(), payload);

        return toDto(request, dataset);
    }

    private boolean canReadRequest(CatalogLifecycleRequest request, CatalogDataset dataset, String activeDept) {
        if (request == null) {
            return false;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
            return true;
        }
        if (dataset == null) {
            return false;
        }
        if (!accessChecker.canRead(dataset)) {
            return false;
        }
        String ownerDept = StringUtils.trimToNull(dataset.getOwnerDept());
        if (ownerDept == null) {
            return false;
        }
        if (organizationVisibilityService.isRoot(ownerDept)) {
            return true;
        }
        return StringUtils.isNotBlank(activeDept) && DepartmentUtils.matches(ownerDept, activeDept);
    }

    private void ensureDecisionAllowed(CatalogLifecycleRequest request, String activeDeptHeader) {
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
            throw new AccessDeniedException("无权审批生命周期申请");
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return;
        }
        String ownerDept = StringUtils.trimToNull(request != null ? request.getOwnerDept() : null);
        if (StringUtils.isBlank(ownerDept)) {
            return;
        }
        if (organizationVisibilityService.isRoot(ownerDept)) {
            return;
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (StringUtils.isBlank(activeDept) || !DepartmentUtils.matches(ownerDept, activeDept)) {
            throw new AccessDeniedException("无权审批该部门的生命周期申请");
        }
    }

    private void ensureDatasetAllowed(CatalogDataset dataset, String activeDeptHeader) {
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (!accessChecker.canRead(dataset)) {
            throw new AccessDeniedException("无权访问该数据集");
        }
        if (!accessChecker.departmentAllowed(dataset, activeDept)) {
            throw new AccessDeniedException("无权访问该数据集");
        }
    }

    private void ensureActionAllowed(CatalogDataset dataset, AssetAction action) {
        if (!accessChecker.canPerform(dataset, action)) {
            throw new AccessDeniedException("asset_action_not_allowed:" + action.code());
        }
    }

    private AssetAction actionForRequestType(String requestType) {
        return switch (normalizeRequestType(requestType)) {
            case TYPE_ARCHIVE -> AssetAction.ARCHIVE;
            case TYPE_DISPOSE -> AssetAction.DELETE;
            case TYPE_EXTEND -> AssetAction.UPDATE;
            default -> throw new IllegalArgumentException("不支持的申请类型");
        };
    }

    private void applyApprovedChange(
        CatalogLifecycleRequest request,
        CatalogDataset dataset,
        String actor
    ) {
        String type = normalizeRequestType(request.getRequestType());
        if (TYPE_DISPOSE.equals(type) && lifecycleControlService != null) {
            lifecycleControlService.recordLegacyTrash(
                dataset.getId(),
                request.getId(),
                StringUtils.defaultIfBlank(request.getCreatedBy(), "system"),
                actor,
                request.getRequestedRetentionDays()
            );
            return;
        }
        if (TYPE_ARCHIVE.equals(type) || TYPE_DISPOSE.equals(type)) {
            dataset.setLifecycleStatus(StringUtils.trimToNull(request.getRequestedLifecycleStatus()));
            if (TYPE_DISPOSE.equals(type)) {
                dataset.setEnabled(Boolean.FALSE);
            }
            return;
        }
        if (TYPE_EXTEND.equals(type)) {
            if (request.getRequestedRetentionDays() != null) {
                dataset.setRetentionDays(request.getRequestedRetentionDays());
            }
            if (request.getRequestedExpiresAt() != null) {
                dataset.setExpiresAt(request.getRequestedExpiresAt());
            }
            return;
        }
    }

    private void revertDataset(CatalogLifecycleRequest request, CatalogDataset dataset) {
        dataset.setLifecycleStatus(StringUtils.trimToNull(request.getPreviousLifecycleStatus()));
        dataset.setRetentionDays(request.getPreviousRetentionDays());
        dataset.setExpiresAt(request.getPreviousExpiresAt());
    }

    private LifecycleRequestDto toDto(CatalogLifecycleRequest request, CatalogDataset dataset) {
        LifecycleRequestDto dto = new LifecycleRequestDto();
        dto.setId(request.getId());
        dto.setDatasetId(request.getDatasetId());
        dto.setDatasetName(dataset != null ? dataset.getName() : null);
        dto.setOwnerDept(StringUtils.trimToNull(request.getOwnerDept()));
        dto.setRequestType(request.getRequestType());
        dto.setStatus(request.getStatus());
        dto.setNotes(request.getNotes());
        dto.setPreviousLifecycleStatus(request.getPreviousLifecycleStatus());
        dto.setRequestedLifecycleStatus(request.getRequestedLifecycleStatus());
        dto.setRequestedRetentionDays(request.getRequestedRetentionDays());
        dto.setRequestedExpiresAt(request.getRequestedExpiresAt());
        dto.setDecidedBy(request.getDecidedBy());
        dto.setDecidedAt(request.getDecidedAt());
        dto.setDecisionNotes(request.getDecisionNotes());
        dto.setCreatedBy(request.getCreatedBy());
        dto.setCreatedDate(request.getCreatedDate());
        return dto;
    }

    private String normalizeRequestType(String raw) {
        String normalized = StringUtils.trimToNull(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("requestType 不能为空");
        }
        normalized = normalized.trim().toUpperCase(Locale.ROOT);
        if (!List.of(TYPE_ARCHIVE, TYPE_DISPOSE, TYPE_EXTEND).contains(normalized)) {
            throw new IllegalArgumentException("不支持的 requestType: " + normalized);
        }
        return normalized;
    }

    private String resolveRequestedLifecycleStatus(String requestType) {
        if (TYPE_ARCHIVE.equals(requestType)) {
            return "ARCHIVED";
        }
        if (TYPE_DISPOSE.equals(requestType)) {
            return "TRASHED";
        }
        return null;
    }

    private String resolvePendingLifecycleStatus(String requestType) {
        if (TYPE_ARCHIVE.equals(requestType)) {
            return "ARCHIVE_REQUESTED";
        }
        if (TYPE_DISPOSE.equals(requestType)) {
            return "TRASH_REQUESTED";
        }
        return null;
    }

    private Integer normalizeRetentionDays(Integer days) {
        if (days == null) {
            return null;
        }
        if (days <= 0) {
            return null;
        }
        return Math.min(days, 36500);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private String resolveActiveDept(String activeDeptHeader) {
        if (org.springframework.util.StringUtils.hasText(activeDeptHeader)) {
            return activeDeptHeader.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String candidate = extractDeptClaim(token.getToken().getClaims().get("dept_code"));
                if (candidate != null) {
                    return candidate;
                }
                candidate = extractDeptClaim(token.getToken().getClaims().get("deptCode"));
                if (candidate != null) {
                    return candidate;
                }
                return extractDeptClaim(token.getToken().getClaims().get("department"));
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                String candidate = extractDeptClaim(principal.getAttribute("dept_code"));
                if (candidate != null) {
                    return candidate;
                }
                candidate = extractDeptClaim(principal.getAttribute("deptCode"));
                if (candidate != null) {
                    return candidate;
                }
                return extractDeptClaim(principal.getAttribute("department"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractDeptClaim(Object raw) {
        Object flattened = flattenValue(raw);
        if (flattened == null) {
            return null;
        }
        String text = flattened.toString();
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Object flattenValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }
}
