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
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
// Batch saves must commit before a REQUIRES_NEW record transaction can reference them.
// Suspend any caller transaction as well; a database rollback cannot undo Keycloak calls.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PersonnelImportService {

    private static final Logger LOG = LoggerFactory.getLogger(PersonnelImportService.class);
    private static final Logger OPS_LOG = LoggerFactory.getLogger("dts.personnel.operations");

    private final PersonImportBatchRepository batchRepository;
    private final PersonImportRecordRepository recordRepository;
    private final PersonnelExcelParser excelParser;
    private final PersonnelApiClient apiClient;
    private final AuditV2Service auditV2Service;
    private final KeycloakUserProvisioningService provisioningService;
    private final AdminKeycloakUserRepository adminKeycloakUserRepository;
    private final ObjectMapper objectMapper;
    private final MdmGatewayProperties mdmGatewayProperties;
    private final PlatformTransactionManager transactionManager;
    private final KeycloakUsernameAllocator usernameAllocator;

    public PersonnelImportService(
        PersonImportBatchRepository batchRepository,
        PersonImportRecordRepository recordRepository,
        PersonnelExcelParser excelParser,
        PersonnelApiClient apiClient,
        AuditV2Service auditV2Service,
        KeycloakUserProvisioningService provisioningService,
        AdminKeycloakUserRepository adminKeycloakUserRepository,
        ObjectMapper objectMapper,
        MdmGatewayProperties mdmGatewayProperties,
        PlatformTransactionManager transactionManager,
        KeycloakUsernameAllocator usernameAllocator
    ) {
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.excelParser = excelParser;
        this.apiClient = apiClient;
        this.auditV2Service = auditV2Service;
        this.provisioningService = provisioningService;
        this.adminKeycloakUserRepository = adminKeycloakUserRepository;
        this.objectMapper = objectMapper;
        this.mdmGatewayProperties = mdmGatewayProperties;
        this.transactionManager = transactionManager;
        this.usernameAllocator = usernameAllocator;
    }

    public PersonnelImportResult importFromApi(String reference, boolean dryRun, String cursor) {
        PersonnelApiClient.ApiFetchResult result = apiClient.fetch(cursor);
        return processBatch(PersonSourceType.API, reference, dryRun, result.records(), List.of(), metadataWithCursor(cursor, result.nextCursor()));
    }

    public PersonnelImportResult importFromExcel(String filename, java.io.InputStream inputStream, boolean dryRun) {
        List<PersonnelPayload> payloads = excelParser.parse(inputStream, filename);
        return processBatch(PersonSourceType.EXCEL, filename, dryRun, payloads, List.of(), Map.of("filename", filename));
    }

    public PersonnelImportResult importManual(String reference, boolean dryRun, List<PersonnelPayload> payloads) {
        return processBatch(PersonSourceType.MANUAL, reference, dryRun, payloads, List.of(), Map.of());
    }

    public PersonnelImportResult importFromMdm(String reference, List<PersonnelPayload> payloads, Map<String, Object> metadata) {
        return importFromMdm(reference, payloads, List.of(), metadata);
    }

    /**
     * MDM 导入：{@code payloads} 正常导入；{@code rejected} 是预校验未通过（如缺必填字段）的记录，
     * 不写 Keycloak，只作为失败明细记入同一批次，保证单条坏数据不会拦住整个文件。
     */
    public PersonnelImportResult importFromMdm(
        String reference,
        List<PersonnelPayload> payloads,
        List<RejectedPayload> rejected,
        Map<String, Object> metadata
    ) {
        return processBatch(PersonSourceType.MDM, reference, false, payloads, rejected, metadata == null ? Map.of() : metadata);
    }

    /** 预校验未通过的一条记录及原因。 */
    public record RejectedPayload(PersonnelPayload payload, String reason) {}

    private PersonnelImportResult processBatch(
        PersonSourceType sourceType,
        String reference,
        boolean dryRun,
        List<PersonnelPayload> payloads,
        List<RejectedPayload> rejected,
        Map<String, Object> metadata
    ) {
        List<PersonnelPayload> accepted = payloads == null ? List.of() : payloads;
        List<RejectedPayload> rejectedRecords = rejected == null ? List.of() : rejected;
        if (accepted.isEmpty() && rejectedRecords.isEmpty()) {
            throw new PersonnelImportException("导入数据为空");
        }
        int total = accepted.size() + rejectedRecords.size();
        PersonImportBatch batch = new PersonImportBatch();
        batch.setSourceType(sourceType);
        batch.setStatus(PersonImportStatus.RUNNING);
        batch.setReference(reference);
        batch.setDryRun(dryRun);
        batch.setStartedAt(Instant.now());
        batch.setTotalRecords(total);
        batch.setMetadata(metadata);
        batch = batchRepository.save(batch);
        OPS_LOG.info(
            "[batch-start] id={} type={} ref={} total={} dryRun={}",
            batch.getId(),
            sourceType,
            reference,
            total,
            dryRun
        );

        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (RejectedPayload rejectedRecord : rejectedRecords) {
            saveRejectedRecordInNewTransaction(batch.getId(), rejectedRecord);
            failed++;
        }
        for (PersonnelPayload payload : accepted) {
            RecordOutcome outcome;
            try {
                outcome = processRecordInNewTransaction(batch.getId(), payload, dryRun);
            } catch (Exception ex) {
                outcome = saveRecordFailureInNewTransaction(batch.getId(), payload, ex);
            }
            success += outcome.success();
            failed += outcome.failed();
            skipped += outcome.skipped();
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
            total,
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

    private RecordOutcome processRecordInNewTransaction(Long batchId, PersonnelPayload payload, boolean dryRun) {
        RecordOutcome outcome = requiresNewTransaction()
            .execute(status -> {
                PersonImportBatch batchRef = batchRepository.getReferenceById(batchId);
                PersonImportRecord record = buildRecord(batchRef, payload);
                RecordOutcome recordOutcome = applyPayload(batchId, record, payload, dryRun);
                record.setProcessedAt(Instant.now());
                recordRepository.save(record);
                return recordOutcome;
            });
        return outcome == null ? RecordOutcome.oneFailed() : outcome;
    }

    private RecordOutcome applyPayload(Long batchId, PersonImportRecord record, PersonnelPayload payload, boolean dryRun) {
        try {
            if (dryRun) {
                record.setStatus(PersonRecordStatus.SKIPPED);
                record.setMessage("Dry-run 模式，未写入 Keycloak");
                return RecordOutcome.oneSkipped();
            }
            String keycloakUsername = usernameAllocator.allocate(payload.personCode(), payload.account());
            KeycloakUserProvisioningService.ProvisionResult provisioned;
            try {
                provisioned = provisioningService.provision(payload, keycloakUsername);
            } catch (RuntimeException ex) {
                // provision 走 @Transactional(MANDATORY)，异常穿过事务代理时当前事务已被标记 rollback-only。
                // 在这里吞掉只会让外层提交抛 UnexpectedRollbackException，失败明细变成无信息的「事务异常」。
                // 原样抛出，由 saveRecordFailureInNewTransaction 在新事务里记录真实原因。
                OPS_LOG.error(
                    "[record-provision-failed] batch={} personCode={} username={} reason={}",
                    batchId,
                    payload.personCode(),
                    keycloakUsername,
                    exceptionMessage(ex)
                );
                throw ex;
            }
            String keycloakUserId = provisioned.keycloakUserId();
            record.setKeycloakUserId(keycloakUserId);
            Map<String, Object> attributes = payload.attributes() == null ? Map.of() : payload.attributes();
            upsertSnapshot(
                keycloakUserId,
                keycloakUsername,
                payload.fullName(),
                attributes.getOrDefault("securityLevel", attributes.get("person_security_level")),
                payload.deptCode(),
                payload.deptName(),
                payload.personCode(),
                provisioned.groupPaths(),
                // 以 Keycloak 实际启用状态为准：新建账号为禁用；已有账号不随同步改变，但快照要与之一致。
                provisioned.enabled(),
                resolveMdmEnabled(payload)
            );
            record.setStatus(PersonRecordStatus.SUCCESS);
            record.setMessage("OK");
            return RecordOutcome.oneSuccess();
        } catch (PersonnelImportException ex) {
            record.setStatus(PersonRecordStatus.FAILED);
            record.setMessage(limitMessage(ex.getMessage()));
            OPS_LOG.warn(
                "[record-fail] batch={} personCode={} reason={} payload={}",
                batchId,
                payload.personCode(),
                ex.getMessage(),
                summarizePayload(payload)
            );
            return RecordOutcome.oneFailed();
        } catch (Exception ex) {
            record.setStatus(PersonRecordStatus.FAILED);
            record.setMessage(limitMessage("处理异常: " + exceptionMessage(ex)));
            OPS_LOG.error(
                "[record-error] batch={} personCode={} payload={} {}",
                batchId,
                payload.personCode(),
                summarizePayload(payload),
                ex.getMessage(),
                ex
            );
            return RecordOutcome.oneFailed();
        }
    }

    private RecordOutcome saveRecordFailureInNewTransaction(Long batchId, PersonnelPayload payload, Exception ex) {
        try {
            requiresNewTransaction()
                .executeWithoutResult(status -> {
                    PersonImportBatch batchRef = batchRepository.getReferenceById(batchId);
                    PersonImportRecord record = buildRecord(batchRef, payload);
                    record.setStatus(PersonRecordStatus.FAILED);
                    record.setMessage(limitMessage("导入失败: " + exceptionMessage(ex)));
                    record.setProcessedAt(Instant.now());
                    recordRepository.save(record);
                });
        } catch (Exception persistEx) {
            OPS_LOG.error(
                "[record-error-persist-fail] batch={} personCode={} payload={} original={} persist={}",
                batchId,
                payload.personCode(),
                summarizePayload(payload),
                exceptionMessage(ex),
                exceptionMessage(persistEx),
                persistEx
            );
        }
        OPS_LOG.error(
            "[record-transaction-error] batch={} personCode={} payload={} {}",
            batchId,
            payload.personCode(),
            summarizePayload(payload),
            exceptionMessage(ex),
            ex
        );
        return RecordOutcome.oneFailed();
    }

    /** 预校验未通过的记录：不调 Keycloak，直接以 FAILED 明细落库，原因写入 message。 */
    private void saveRejectedRecordInNewTransaction(Long batchId, RejectedPayload rejected) {
        PersonnelPayload payload = rejected.payload();
        try {
            requiresNewTransaction()
                .executeWithoutResult(status -> {
                    PersonImportBatch batchRef = batchRepository.getReferenceById(batchId);
                    PersonImportRecord record = buildRecord(batchRef, payload);
                    record.setStatus(PersonRecordStatus.FAILED);
                    record.setMessage(limitMessage(rejected.reason()));
                    record.setProcessedAt(Instant.now());
                    recordRepository.save(record);
                });
            OPS_LOG.warn(
                "[record-rejected] batch={} personCode={} reason={}",
                batchId,
                payload.personCode(),
                rejected.reason()
            );
        } catch (Exception ex) {
            OPS_LOG.error(
                "[record-rejected-persist-fail] batch={} personCode={} reason={} persist={}",
                batchId,
                payload.personCode(),
                rejected.reason(),
                exceptionMessage(ex),
                ex
            );
        }
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private String exceptionMessage(Throwable ex) {
        String message = ex == null ? null : ex.getMessage();
        return StringUtils.isBlank(message) && ex != null ? ex.getClass().getSimpleName() : message;
    }

    private String limitMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1900 ? message.substring(0, 1900) : message;
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

    /**
     * 把本次导入的结果回写到 Keycloak 用户快照。
     *
     * <p>{@code deptCode}/{@code deptName} 是 Keycloak user attribute 的本地镜像，页面部门显示
     * 由此而来；{@code groupPaths} 是部门组同步后该用户在 Keycloak 中的完整组路径（旧部门组已摘除，
     * 非部门组保留），按「全量覆盖」写入；为 {@code null} 表示无法确定，保持快照不变。
     */
    private void upsertSnapshot(
        String keycloakUserId,
        String username,
        String fullName,
        Object secLevelObj,
        String deptCode,
        String deptName,
        String personCode,
        List<String> groupPaths,
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
        // 定位顺序：Keycloak ID → 原始人员编码（区分大小写）→ 分配到的用户名。
        // 编码优先于用户名，只差大小写的两个编码才能各自对应到自己的快照。
        String code = StringUtils.trimToNull(personCode);
        AdminKeycloakUser snapshot = adminKeycloakUserRepository
            .findByKeycloakId(keycloakUserId)
            .or(() -> code == null ? Optional.empty() : adminKeycloakUserRepository.findFirstByPersonCode(code))
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
        if (StringUtils.isNotBlank(personCode)) {
            snapshot.setPersonCode(personCode.trim());
        }
        if (StringUtils.isNotBlank(deptCode)) {
            snapshot.setDeptCode(deptCode.trim());
        }
        if (StringUtils.isNotBlank(deptName)) {
            snapshot.setDeptName(deptName.trim());
        }
        if (groupPaths != null) {
            // 用可变集合，避免 Hibernate 对 jsonb 属性做脏检查时持有不可变 List。
            List<String> normalized = new java.util.ArrayList<>();
            for (String path : groupPaths) {
                String value = normalizeGroupPath(path);
                if (StringUtils.isNotBlank(value) && normalized.stream().noneMatch(value::equalsIgnoreCase)) {
                    normalized.add(value);
                }
            }
            snapshot.setGroupPaths(normalized);
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

    private record RecordOutcome(int success, int failed, int skipped) {
        private static RecordOutcome oneSuccess() {
            return new RecordOutcome(1, 0, 0);
        }

        private static RecordOutcome oneFailed() {
            return new RecordOutcome(0, 1, 0);
        }

        private static RecordOutcome oneSkipped() {
            return new RecordOutcome(0, 0, 1);
        }
    }
}
