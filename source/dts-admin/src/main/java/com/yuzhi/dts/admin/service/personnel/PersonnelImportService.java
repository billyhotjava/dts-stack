package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.domain.PersonImportBatch;
import com.yuzhi.dts.admin.domain.PersonImportRecord;
import com.yuzhi.dts.admin.domain.enumeration.PersonImportStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonRecordStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonSourceType;
import com.yuzhi.dts.admin.repository.PersonImportBatchRepository;
import com.yuzhi.dts.admin.repository.PersonImportRecordRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditOperation;
import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.service.audit.AuditOperationKind;
import com.yuzhi.dts.admin.service.audit.AuditResultStatus;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.audit.ButtonCodes;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelImportResult;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final KeycloakUserProvisioningService provisioningService;
    private final AdminKeycloakUserRepository adminKeycloakUserRepository;
    private final ObjectMapper objectMapper;
    private final MdmGatewayProperties mdmGatewayProperties;

    public PersonnelImportService(
        PersonImportBatchRepository batchRepository,
        PersonImportRecordRepository recordRepository,
        PersonnelProfileService profileService,
        PersonnelExcelParser excelParser,
        PersonnelApiClient apiClient,
        AuditV2Service auditV2Service,
        KeycloakUserProvisioningService provisioningService,
        AdminKeycloakUserRepository adminKeycloakUserRepository,
        ObjectMapper objectMapper,
        MdmGatewayProperties mdmGatewayProperties
    ) {
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.profileService = profileService;
        this.excelParser = excelParser;
        this.apiClient = apiClient;
        this.auditV2Service = auditV2Service;
        this.provisioningService = provisioningService;
        this.adminKeycloakUserRepository = adminKeycloakUserRepository;
        this.objectMapper = objectMapper;
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
                    record.setMessage("Dry-run 模式，未写入 Keycloak");
                    skipped++;
                } else {
                    String keycloakUserId = provisioningService.provision(payload);
                    record.setKeycloakUserId(keycloakUserId);
                    upsertSnapshot(
                        keycloakUserId,
                        firstNonBlank(payload.account(), payload.personCode()),
                        payload.fullName(),
                        payload.attributes().getOrDefault("securityLevel", payload.attributes().get("person_security_level")),
                        null,
                        null,
                        resolveMdmEnabled(payload)
                    );
                    record.setStatus(PersonRecordStatus.SUCCESS);
                    record.setMessage("OK");
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

    private void upsertSnapshot(
        String keycloakUserId,
        String username,
        String fullName,
        Object secLevelObj,
        String groupPath,
        Boolean keycloakEnabled,
        Integer mdmEnabled
    ) {
        if (StringUtils.isBlank(keycloakUserId) || StringUtils.isBlank(username)) {
            return;
        }
        String level = normalizeSecurityLevel(secLevelObj == null ? null : String.valueOf(secLevelObj));
        if (StringUtils.isBlank(level)) {
            level = SecurityLevelCatalog.DEFAULT_PERSONNEL_SECURITY_LEVEL.code();
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
        if (keycloakEnabled != null) {
            snapshot.setEnabled(Boolean.TRUE.equals(keycloakEnabled));
        }
        if (mdmEnabled != null) {
            snapshot.setMdmEnabled(mdmEnabled);
        }
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

    private int resolveMdmEnabled(PersonnelPayload payload) {
        if (payload == null) {
            return 1;
        }
        Object raw = payload.attributes() == null ? null : payload.attributes().get("status");
        if (raw != null) {
            String v = String.valueOf(raw).trim();
            if ("0".equals(v)) {
                return 0;
            }
            if ("1".equals(v)) {
                return 1;
            }
        }
        String lifecycle = payload.status() == null ? "" : payload.status().trim().toUpperCase(Locale.ROOT);
        if ("INACTIVE".equals(lifecycle) || "DISABLED".equals(lifecycle)) {
            return 0;
        }
        return 1;
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

    private String normalizeSecurityLevel(String level) {
        return SecurityLevelCatalog.normalizePersonnelCode(level);
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
