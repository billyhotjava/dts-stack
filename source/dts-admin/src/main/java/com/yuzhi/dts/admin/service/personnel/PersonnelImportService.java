package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.domain.PersonImportBatch;
import com.yuzhi.dts.admin.domain.PersonImportRecord;
import com.yuzhi.dts.admin.domain.enumeration.PersonImportStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonRecordStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonSourceType;
import com.yuzhi.dts.admin.repository.PersonImportBatchRepository;
import com.yuzhi.dts.admin.repository.PersonImportRecordRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.auditv2.AdminAuditOperation;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelImportResult;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService.TokenResponse;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonnelImportService {

    private static final Logger LOG = LoggerFactory.getLogger(PersonnelImportService.class);
    private static final Logger OPS_LOG = LoggerFactory.getLogger("dts.personnel.operations");

    private final PersonImportBatchRepository batchRepository;
    private final PersonImportRecordRepository recordRepository;
    private final PersonnelProfileService profileService;
    private final PersonnelExcelParser excelParser;
    private final PersonnelApiClient apiClient;
    private final AuditV2Service auditV2Service;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAuthService keycloakAuthService;
    private final AdminKeycloakUserRepository adminKeycloakUserRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;
    private final String managementClientId;
    private final String managementClientSecret;
    private final MdmGatewayProperties mdmGatewayProperties;
    private static final String RANDOM_PASSWORD_PREFIX = "mdm$";

    public PersonnelImportService(
        PersonImportBatchRepository batchRepository,
        PersonImportRecordRepository recordRepository,
        PersonnelProfileService profileService,
        PersonnelExcelParser excelParser,
        PersonnelApiClient apiClient,
        AuditV2Service auditV2Service,
        KeycloakAdminClient keycloakAdminClient,
        KeycloakAuthService keycloakAuthService,
        AdminKeycloakUserRepository adminKeycloakUserRepository,
        OrganizationRepository organizationRepository,
        ObjectMapper objectMapper,
        @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}") String managementClientId,
        @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}") String managementClientSecret,
        MdmGatewayProperties mdmGatewayProperties
    ) {
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.profileService = profileService;
        this.excelParser = excelParser;
        this.apiClient = apiClient;
        this.auditV2Service = auditV2Service;
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakAuthService = keycloakAuthService;
        this.adminKeycloakUserRepository = adminKeycloakUserRepository;
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret.trim();
        this.mdmGatewayProperties = mdmGatewayProperties;
    }

    public PersonnelImportResult importFromApi(String reference, boolean dryRun, String cursor) {
        PersonnelApiClient.ApiFetchResult result = apiClient.fetch(cursor);
        return processBatch(PersonSourceType.API, reference, dryRun, result.records(), metadataWithCursor(cursor, result.nextCursor()));
    }

    public PersonnelImportResult importFromExcel(String filename, java.io.InputStream inputStream, boolean dryRun) {
        List<PersonnelPayload> payloads = excelParser.parse(inputStream, filename);
        return processBatch(PersonSourceType.EXCEL, filename, dryRun, payloads, Map.of("filename", filename));
    }

    public PersonnelImportResult importManual(String reference, boolean dryRun, List<PersonnelPayload> payloads) {
        return processBatch(PersonSourceType.MANUAL, reference, dryRun, payloads, Map.of());
    }

    public PersonnelImportResult importFromMdm(String reference, List<PersonnelPayload> payloads, Map<String, Object> metadata) {
        return processBatch(PersonSourceType.MDM, reference, false, payloads, metadata == null ? Map.of() : metadata);
    }

    private PersonnelImportResult processBatch(
        PersonSourceType sourceType,
        String reference,
        boolean dryRun,
        List<PersonnelPayload> payloads,
        Map<String, Object> metadata
    ) {
        if (payloads == null || payloads.isEmpty()) {
            throw new PersonnelImportException("导入数据为空");
        }
        PersonImportBatch batch = new PersonImportBatch();
        batch.setSourceType(sourceType);
        batch.setStatus(PersonImportStatus.RUNNING);
        batch.setReference(reference);
        batch.setDryRun(dryRun);
        batch.setStartedAt(Instant.now());
        batch.setTotalRecords(payloads.size());
        batch.setMetadata(metadata);
        batch = batchRepository.save(batch);
        OPS_LOG.info(
            "[batch-start] id={} type={} ref={} total={} dryRun={}",
            batch.getId(),
            sourceType,
            reference,
            payloads.size(),
            dryRun
        );

        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (PersonnelPayload payload : payloads) {
            PersonImportRecord record = buildRecord(batch, payload);
            try {
                if (dryRun) {
                    record.setStatus(PersonRecordStatus.SKIPPED);
                    record.setMessage("Dry-run 模式，未写入主数据");
                    skipped++;
                } else {
                    var profile = profileService.upsert(payload, batch.getId(), sourceType, reference);
                    record.setProfileId(profile.getId());
                    record.setStatus(PersonRecordStatus.SUCCESS);
                    record.setMessage("OK");
                    provisionKeycloakUser(payload);
                    success++;
                }
            } catch (PersonnelImportException ex) {
                record.setStatus(PersonRecordStatus.FAILED);
                record.setMessage(ex.getMessage());
                failed++;
                OPS_LOG.warn(
                    "[record-fail] batch={} personCode={} reason={} payload={}",
                    batch.getId(),
                    payload.personCode(),
                    ex.getMessage(),
                    summarizePayload(payload)
                );
            } catch (Exception ex) {
                record.setStatus(PersonRecordStatus.FAILED);
                record.setMessage("处理异常: " + ex.getMessage());
                failed++;
                OPS_LOG.error(
                    "[record-error] batch={} personCode={} payload={} {}",
                    batch.getId(),
                    payload.personCode(),
                    summarizePayload(payload),
                    ex.getMessage(),
                    ex
                );
            }
            record.setProcessedAt(Instant.now());
            recordRepository.save(record);
        }

        batch.setSuccessRecords(success);
        batch.setFailureRecords(failed);
        batch.setSkippedRecords(skipped);
        batch.setCompletedAt(Instant.now());
        batch.setStatus(resolveStatus(success, failed));
        if (failed > 0) {
            batch.setErrorMessage("有 " + failed + " 条记录导入失败");
        }
        batch = batchRepository.save(batch);
        OPS_LOG.info(
            "[batch-end] id={} type={} total={} success={} failed={} skipped={} status={} dryRun={}",
            batch.getId(),
            sourceType,
            payloads.size(),
            success,
            failed,
            skipped,
            batch.getStatus(),
            dryRun
        );
        recordAudit(batch, sourceType, success, failed);
        return toResult(batch);
    }

    private PersonImportRecord buildRecord(PersonImportBatch batch, PersonnelPayload payload) {
        PersonImportRecord record = new PersonImportRecord();
        record.setBatch(batch);
        record.setPersonCode(payload.personCode());
        record.setExternalId(payload.externalId());
        record.setAccount(payload.account());
        record.setFullName(payload.fullName());
        record.setNationalId(payload.nationalId());
        record.setDeptCode(payload.deptCode());
        record.setDeptName(payload.deptName());
        record.setDeptPath(payload.deptPath());
        record.setTitle(payload.title());
        record.setGrade(payload.grade());
        record.setEmail(payload.email());
        record.setPhone(payload.phone());
        record.setActiveFrom(payload.activeFrom());
        record.setActiveTo(payload.activeTo());
        record.setPayload(payload.safeAttributes());
        record.setAttributes(payload.safeAttributes());
        return record;
    }

    private String summarizePayload(PersonnelPayload payload) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("personCode", payload.personCode());
            map.put("account", payload.account());
            map.put("userName", payload.fullName());
            map.put("deptCode", payload.deptCode());
            map.put("deptName", payload.deptName());
            map.put("status", payload.status());
            map.put("securityLevel", payload.attributes().get("securityLevel"));
            map.put("person_security_level", payload.attributes().get("person_security_level"));
            map.put("deptPath", payload.deptPath());
            map.put("attrs", payload.safeAttributes());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return String.valueOf(payload.safeAttributes());
        }
    }

    private PersonImportStatus resolveStatus(int success, int failed) {
        if (failed > 0 && success == 0) {
            return PersonImportStatus.FAILED;
        }
        if (failed > 0) {
            return PersonImportStatus.COMPLETED_WITH_ERRORS;
        }
        return PersonImportStatus.COMPLETED;
    }

    private Map<String, Object> metadataWithCursor(String cursor, String nextCursor) {
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isNotBlank(cursor)) {
            map.put("cursor", cursor);
        }
        if (StringUtils.isNotBlank(nextCursor)) {
            map.put("nextCursor", nextCursor);
        }
        return map;
    }

    private void recordAudit(PersonImportBatch batch, PersonSourceType sourceType, int success, int failed) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        boolean hasFailure = failed > 0;
        String summary = "人员主数据导入-" + sourceType.name().toLowerCase(Locale.ROOT);
        Map<String, Object> detail = new HashMap<>();
        detail.put("batchId", batch.getId());
        detail.put("sourceType", sourceType.name());
        detail.put("reference", batch.getReference());
        detail.put("success", success);
        detail.put("failed", failed);
        detail.put("skipped", batch.getSkippedRecords());

        AuditActionRequest.Builder builder = AuditActionRequest
            .builder(actor, resolveButtonCode(sourceType))
            .summary(summary)
            .result(hasFailure ? AuditResultStatus.FAILED : AuditResultStatus.SUCCESS)
            .detail("statistics", detail)
            .allowEmptyTargets()
            .metadata("batchId", String.valueOf(batch.getId()));

        AdminAuditOperation operation = resolveAuditOperation(sourceType);
        builder.operationOverride(operation.code(), summary, AuditOperationKind.IMPORT);
        builder.moduleOverride(operation.moduleKey(), operation.moduleLabel());
        auditV2Service.record(builder.build());
    }

    private void provisionKeycloakUser(PersonnelPayload payload) {
        if (mdmGatewayProperties == null || !mdmGatewayProperties.isAutoProvisionUsers()) {
            return;
        }
        String username = firstNonBlank(payload.account(), payload.personCode());
        if (!StringUtils.isNotBlank(username)) {
            return;
        }
        String token = resolveManagementToken();
        if (token == null) {
            OPS_LOG.warn("skip keycloak provisioning for user {}: management token unavailable", username);
            return;
        }
        try {
            var existingOpt = keycloakAdminClient.findByUsername(username, token);
            if (existingOpt.isPresent()) {
                KeycloakUserDTO existing = existingOpt.orElseThrow();
                boolean dirty = false;
                if (!StringUtils.equals(existing.getFullName(), payload.fullName())) {
                    existing.setFullName(payload.fullName());
                    existing.setFirstName(payload.fullName());
                    dirty = true;
                }
                Map<String, List<String>> desiredAttrs = toKcAttributes(payload);
                if (!attributesEqual(existing.getAttributes(), desiredAttrs)) {
                    existing.setAttributes(desiredAttrs);
                    dirty = true;
                }
                if (dirty) {
                    keycloakAdminClient.updateUser(existing.getId(), existing, token);
                }
                upsertSnapshot(existing.getId(), username, payload.fullName(), desiredAttrs.get("person_security_level"), null);
                assignBaseRoles(existing.getId(), token);
                assignDeptGroup(existing.getId(), payload, token);
                return;
            }
            KeycloakUserDTO dto = new KeycloakUserDTO();
            dto.setUsername(username);
            dto.setFullName(payload.fullName());
            dto.setFirstName(payload.fullName());
            dto.setEnabled(true);
            dto.setEmailVerified(false);
            dto.setAttributes(toKcAttributes(payload));
            KeycloakUserDTO created = keycloakAdminClient.createUser(dto, token);
            String kcId = created != null && StringUtils.isNotBlank(created.getId()) ? created.getId() : dto.getId();
            // 设定随机密码（临时），PKI 环境不强依赖密码，但可避免无口令账号
            if (mdmGatewayProperties.isAutoProvisionEnableLogin()) {
                String pwd = RANDOM_PASSWORD_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                try {
                    if (StringUtils.isNotBlank(kcId)) {
                        keycloakAdminClient.resetPassword(kcId, pwd, true, token);
                    }
                } catch (Exception ex) {
                    OPS_LOG.warn("set temp password failed for user {}: {}", username, ex.getMessage());
                }
            }
            upsertSnapshot(kcId, username, payload.fullName(), dto.getAttributes().get("person_security_level"), null);
            assignBaseRoles(kcId, token);
            assignDeptGroup(kcId, payload, token);
            // 若有部门组同步，可在此按 deptCode 挂组；依赖外层已开启组同步
        } catch (Exception ex) {
            OPS_LOG.warn("auto-provision keycloak user failed: username={} reason={}", username, ex.getMessage());
        }
    }

    private boolean attributesEqual(Map<String, List<String>> left, Map<String, List<String>> right) {
        return normalizeAttributes(left).equals(normalizeAttributes(right));
    }

    private Map<String, List<String>> normalizeAttributes(Map<String, List<String>> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new HashMap<>();
        source.forEach((k, v) -> {
            List<String> vals = v == null
                ? List.of()
                : v
                    .stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(StringUtils::trimToEmpty)
                    .sorted()
                    .toList();
            normalized.put(k, vals);
        });
        return normalized;
    }

    private Map<String, List<String>> toKcAttributes(PersonnelPayload payload) {
        Map<String, List<String>> attrs = new HashMap<>();
        String secLevelRaw = safeString(payload.attributes().getOrDefault("securityLevel", payload.attributes().get("person_security_level")));
        String secLevel = normalizeSecurityLevel(secLevelRaw);
        if (StringUtils.isBlank(secLevel)) {
            secLevel = "GENERAL";
        }
        attrs.put("person_security_level", List.of(secLevel));
        attrs.put("person_level", List.of(secLevel));
        attrs.put("deptCode", List.of(safeString(payload.deptCode())));
        attrs.put("deptName", List.of(safeString(payload.deptName())));
        attrs.put("deptPath", List.of(safeString(payload.deptPath())));
        attrs.put("dept_code", List.of(safeString(payload.deptCode())));
        attrs.put("externalId", List.of(safeString(payload.externalId())));
        attrs.put("nationalId", List.of(safeString(payload.nationalId())));
        attrs.put("fullName", List.of(safeString(payload.fullName())));
        payload.safeAttributes().forEach((k, v) -> {
            if (v != null) {
                attrs.putIfAbsent(k, List.of(String.valueOf(v)));
            }
        });
        return attrs;
    }

    private void persistUserGroupPath(String keycloakUserId, PersonnelPayload payload, String rawPath) {
        String normalized = normalizeGroupPath(rawPath);
        if (StringUtils.isBlank(keycloakUserId) || StringUtils.isBlank(normalized)) {
            return;
        }
        upsertSnapshot(keycloakUserId, firstNonBlank(payload.account(), payload.personCode()), payload.fullName(), payload.attributes().get("person_security_level"), normalized);
    }

    private void upsertSnapshot(
        String keycloakUserId,
        String username,
        String fullName,
        Object secLevelObj,
        String groupPath
    ) {
        if (StringUtils.isBlank(keycloakUserId) || StringUtils.isBlank(username)) {
            return;
        }
        String level = normalizeSecurityLevel(secLevelObj == null ? null : String.valueOf(secLevelObj));
        if (StringUtils.isBlank(level)) {
            level = "GENERAL";
        }
        AdminKeycloakUser snapshot = adminKeycloakUserRepository
            .findByKeycloakId(keycloakUserId)
            .orElseGet(() -> adminKeycloakUserRepository.findByUsernameIgnoreCase(username).orElseGet(AdminKeycloakUser::new));
        snapshot.setKeycloakId(keycloakUserId);
        snapshot.setUsername(username);
        if (StringUtils.isNotBlank(fullName)) {
            snapshot.setFullName(fullName);
        }
        snapshot.setPersonSecurityLevel(level);
        snapshot.setEnabled(true);
        if (StringUtils.isNotBlank(groupPath)) {
            String normalized = normalizeGroupPath(groupPath);
            if (StringUtils.isNotBlank(normalized)) {
                List<String> paths = new java.util.ArrayList<>(snapshot.getGroupPaths() == null ? List.of() : snapshot.getGroupPaths());
                boolean exists = paths.stream().map(this::normalizeGroupPath).anyMatch(normalized::equalsIgnoreCase);
                if (!exists) {
                    paths.add(normalized);
                    snapshot.setGroupPaths(paths);
                }
            }
        }
        snapshot.setLastSyncAt(Instant.now());
        adminKeycloakUserRepository.save(snapshot);
    }

    private String normalizeGroupPath(String path) {
        if (!StringUtils.isNotBlank(path)) {
            return null;
        }
        String trimmed = path.trim().replaceAll("/{2,}", "/");
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String safeString(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private String normalizeSecurityLevel(String level) {
        if (level == null) {
            return null;
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        // 与 AdminUserService 的映射保持一致，补充数字密级到平台枚举
        return switch (normalized) {
            case "0" -> "GENERAL"; // 院方：0=一般
            case "1", "IMPORTANT", "IM", "I" -> "IMPORTANT"; // 院方：1=重要
            case "2", "CORE", "CO", "C" -> "CORE"; // 院方：2=核心
            case "GENERAL", "GN", "GE", "G" -> "GENERAL";
            case "NONE_SECRET", "NON_SECRET", "NS" -> "GENERAL"; // 兼容旧值，收敛为 GENERAL
            default -> normalized;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private void assignBaseRoles(String userId, String token) {
        if (userId == null || token == null) {
            return;
        }
        String rolesCsv = mdmGatewayProperties.getAutoProvisionRoles();
        if (!StringUtils.isNotBlank(rolesCsv)) {
            return;
        }
        List<String> roles = Arrays.stream(rolesCsv.split(",")).map(String::trim).filter(StringUtils::isNotBlank).toList();
        if (roles.isEmpty()) {
            return;
        }
        try {
            keycloakAdminClient.addRealmRolesToUser(userId, roles, token);
        } catch (Exception ex) {
            OPS_LOG.warn("assign base roles failed for user {} roles={} reason={}", userId, roles, ex.getMessage());
        }
    }

    private void assignDeptGroup(String userId, PersonnelPayload payload, String token) {
        if (userId == null || token == null) {
            return;
        }
        String deptCode = safeString(payload.deptCode());
        if (!StringUtils.isNotBlank(deptCode)) {
            return;
        }
        organizationRepository
            .findFirstByDeptCodeIgnoreCase(deptCode)
            .ifPresent(node -> {
                String groupId = node.getKeycloakGroupId();
                String groupPath = buildGroupPath(node);
                if (StringUtils.isBlank(groupId)) {
                    if (StringUtils.isBlank(groupPath)) {
                        groupPath = buildGroupPathFromRepository(node);
                    }
                    if (StringUtils.isNotBlank(groupPath)) {
                        keycloakAdminClient.findGroupByPath(groupPath, token).ifPresent(found -> {
                            node.setKeycloakGroupId(found.getId());
                            organizationRepository.save(node);
                        });
                        groupId = node.getKeycloakGroupId();
                    }
                }
                if (StringUtils.isNotBlank(groupId)) {
                    try {
                        keycloakAdminClient.addUserToGroup(userId, groupId, token);
                        persistUserGroupPath(userId, payload, groupPath);
                        OPS_LOG.info("bind user {} to dept {} group {}", userId, deptCode, groupId);
                    } catch (Exception ex) {
                        OPS_LOG.warn("bind user {} to org {} failed: {}", userId, deptCode, ex.getMessage());
                    }
                } else {
                    OPS_LOG.warn(
                        "skip binding user {}: dept {} has no Keycloak group id; set dts.keycloak.group-provisioning-enabled=true and推送组织树",
                        userId,
                        deptCode
                    );
                }
            });
    }

    private String buildGroupPath(OrganizationNode node) {
        if (node == null) {
            return null;
        }
        List<String> segments = new java.util.ArrayList<>();
        OrganizationNode cursor = node;
        while (cursor != null) {
            String name = StringUtils.trimToNull(cursor.getName());
            if (name != null) {
                segments.add(0, name);
            }
            cursor = cursor.getParent();
        }
        if (segments.isEmpty()) {
            return null;
        }
        return "/" + String.join("/", segments);
    }

    /**
     * 当 JPA 未加载 parent 时，基于 parentCode 逐级查询数据库补齐路径。
     */
    private String buildGroupPathFromRepository(OrganizationNode node) {
        if (node == null) {
            return null;
        }
        List<String> segments = new java.util.ArrayList<>();
        OrganizationNode cursor = node;
        int guard = 20; // 防止环
        while (cursor != null && guard-- > 0) {
            String name = StringUtils.trimToNull(cursor.getName());
            if (name != null) {
                segments.add(0, name);
            }
            OrganizationNode parent = cursor.getParent();
            if (parent == null && StringUtils.isNotBlank(cursor.getParentCode())) {
                parent = organizationRepository.findFirstByDeptCodeIgnoreCase(cursor.getParentCode()).orElse(null);
            }
            cursor = parent;
        }
        if (segments.isEmpty()) {
            return null;
        }
        return "/" + String.join("/", segments);
    }

    private String resolveManagementToken() {
        if (!StringUtils.isNotBlank(managementClientId)) {
            OPS_LOG.warn("skip keycloak auto-provision: management clientId missing");
            return null;
        }
        try {
            TokenResponse sa = keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret);
            return sa.accessToken();
        } catch (Exception ex) {
            OPS_LOG.warn("skip keycloak auto-provision: cannot obtain service token ({})", ex.getMessage());
            return null;
        }
    }

    private String resolveButtonCode(PersonSourceType sourceType) {
        return switch (sourceType) {
            case API -> ButtonCodes.MASTERDATA_PERSON_IMPORT_API;
            case EXCEL -> ButtonCodes.MASTERDATA_PERSON_IMPORT_EXCEL;
            default -> ButtonCodes.MASTERDATA_PERSON_IMPORT_MANUAL;
        };
    }

    private AdminAuditOperation resolveAuditOperation(PersonSourceType type) {
        return switch (type) {
            case API -> AdminAuditOperation.ADMIN_PERSON_IMPORT_API;
            case EXCEL -> AdminAuditOperation.ADMIN_PERSON_IMPORT_EXCEL;
            default -> AdminAuditOperation.ADMIN_PERSON_IMPORT_MANUAL;
        };
    }

    private PersonnelImportResult toResult(PersonImportBatch batch) {
        return new PersonnelImportResult(
            batch.getId(),
            batch.getStatus().name(),
            batch.getTotalRecords() == null ? 0 : batch.getTotalRecords(),
            batch.getSuccessRecords() == null ? 0 : batch.getSuccessRecords(),
            batch.getFailureRecords() == null ? 0 : batch.getFailureRecords(),
            batch.getSkippedRecords() == null ? 0 : batch.getSkippedRecords(),
            batch.isDryRun()
        );
    }
}
