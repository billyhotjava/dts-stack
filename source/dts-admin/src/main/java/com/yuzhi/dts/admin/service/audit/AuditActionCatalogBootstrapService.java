package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.domain.audit.AuditActionCatalogEntry;
import com.yuzhi.dts.admin.domain.audit.AuditModuleCatalog;
import com.yuzhi.dts.admin.repository.audit.AuditActionCatalogRepository;
import com.yuzhi.dts.admin.repository.audit.AuditModuleCatalogRepository;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuditActionCatalogBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AuditActionCatalogBootstrapService.class);
    private static final String SOURCE_SYSTEM_PLATFORM = "platform";
    private static final String VERSION = "common-catalog-bootstrap";

    private final AuditActionCatalog commonCatalog;
    private final AuditModuleCatalogRepository moduleRepository;
    private final AuditActionCatalogRepository actionRepository;

    public AuditActionCatalogBootstrapService(
        AuditActionCatalog commonCatalog,
        AuditModuleCatalogRepository moduleRepository,
        AuditActionCatalogRepository actionRepository
    ) {
        this.commonCatalog = Objects.requireNonNull(commonCatalog, "commonCatalog required");
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository required");
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository required");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMissingCatalogEntries() {
        List<AuditActionDefinition> definitions = commonCatalog.listAll();
        int insertedModules = 0;
        int insertedActions = 0;
        Set<String> checkedModules = new HashSet<>();
        for (AuditActionDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getCode())) {
                continue;
            }
            String actionCode = normalizeCode(definition.getCode());
            if (
                actionRepository
                    .findFirstBySourceSystemIgnoreCaseAndActionCodeIgnoreCase(SOURCE_SYSTEM_PLATFORM, actionCode)
                    .isPresent()
            ) {
                continue;
            }
            String moduleKey = normalizeKey(firstNonBlank(definition.getEntryKey(), definition.getModuleKey(), "platform.general"));
            String moduleName = firstNonBlank(definition.getEntryTitle(), definition.getModuleTitle(), moduleKey);
            if (checkedModules.add(moduleKey) && moduleMissing(moduleKey)) {
                moduleRepository.save(buildModule(moduleKey, moduleName));
                insertedModules++;
            }
            actionRepository.save(buildAction(definition, actionCode, moduleKey, moduleName));
            insertedActions++;
        }
        if (insertedModules > 0 || insertedActions > 0) {
            log.info(
                "Seeded missing platform audit catalog entries from common catalog: modules={}, actions={}",
                insertedModules,
                insertedActions
            );
        }
    }

    private boolean moduleMissing(String moduleKey) {
        return moduleRepository
            .findFirstBySourceSystemIgnoreCaseAndModuleKeyIgnoreCase(SOURCE_SYSTEM_PLATFORM, moduleKey)
            .isEmpty();
    }

    private AuditModuleCatalog buildModule(String moduleKey, String moduleName) {
        AuditModuleCatalog module = new AuditModuleCatalog();
        module.setSourceSystem(SOURCE_SYSTEM_PLATFORM);
        module.setModuleKey(moduleKey);
        module.setModuleName(moduleName);
        module.setParentModuleKey(parentModuleKey(moduleKey));
        module.setOwner("platform");
        module.setEnabled(Boolean.TRUE);
        module.setOrderValue(5000);
        module.setVersion(VERSION);
        module.setUpdatedAt(Instant.now());
        return module;
    }

    private AuditActionCatalogEntry buildAction(
        AuditActionDefinition definition,
        String actionCode,
        String moduleKey,
        String moduleName
    ) {
        AuditActionCatalogEntry entry = new AuditActionCatalogEntry();
        entry.setSourceSystem(SOURCE_SYSTEM_PLATFORM);
        entry.setActionCode(actionCode);
        entry.setModuleKey(moduleKey);
        entry.setModuleName(moduleName);
        entry.setOperationCode(actionCode);
        entry.setOperationName(firstNonBlank(definition.getDisplay(), actionCode));
        entry.setOperationKind(inferOperationKind(actionCode, definition.getDisplay()).code());
        entry.setResourceType(moduleKey);
        entry.setAllowEmptyTargets(Boolean.TRUE);
        entry.setEnabled(Boolean.TRUE);
        entry.setVersion(VERSION);
        entry.setUpdatedAt(Instant.now());
        return entry;
    }

    private AuditOperationKind inferOperationKind(String actionCode, String display) {
        String text = (firstNonBlank(actionCode, "") + " " + firstNonBlank(display, "")).toUpperCase(Locale.ROOT);
        if (containsAny(text, "REJECT", "驳回")) return AuditOperationKind.REJECT;
        if (containsAny(text, "APPROVE", "DECIDE", "批准", "审批")) return AuditOperationKind.APPROVE;
        if (containsAny(text, "REVOKE", "CANCEL", "撤销", "取消")) return AuditOperationKind.REVOKE;
        if (containsAny(text, "GRANT", "SHARE", "授权", "共享")) return AuditOperationKind.GRANT;
        if (containsAny(text, "DELETE", "REMOVE", "DROP", "PURGE", "删除", "移除", "清理")) return AuditOperationKind.DELETE;
        if (containsAny(text, "ARCHIVE", "归档")) return AuditOperationKind.ARCHIVE;
        if (containsAny(text, "PUBLISH", "发布")) return AuditOperationKind.PUBLISH;
        if (containsAny(text, "IMPORT", "导入")) return AuditOperationKind.IMPORT;
        if (containsAny(text, "EXPORT", "导出")) return AuditOperationKind.EXPORT;
        if (containsAny(text, "UPLOAD", "上传")) return AuditOperationKind.UPLOAD;
        if (containsAny(text, "DOWNLOAD", "下载")) return AuditOperationKind.DOWNLOAD;
        if (containsAny(text, "REFRESH", "SYNC", "刷新", "同步")) return AuditOperationKind.REFRESH;
        if (containsAny(text, "TEST", "VALIDATE", "检测", "校验", "测试")) return AuditOperationKind.TEST;
        if (containsAny(text, "EXECUTE", "TRIGGER", "RUN", "COMPILE", "APPLY", "执行", "触发", "运行", "应用")) {
            return AuditOperationKind.EXECUTE;
        }
        if (containsAny(text, "CREATE", "NEW", "SUBMIT", "DRAFT", "新增", "新建", "创建", "提交")) return AuditOperationKind.CREATE;
        if (containsAny(text, "UPDATE", "EDIT", "SAVE", "SET", "REPLACE", "ENABLE", "DISABLE", "修改", "编辑", "维护", "保存", "启用", "禁用")) {
            return AuditOperationKind.UPDATE;
        }
        if (containsAny(text, "LIST", "VIEW", "READ", "QUERY", "SEARCH", "STATUS", "PREVIEW", "PAGE", "查看", "查询", "预览")) {
            return AuditOperationKind.QUERY;
        }
        return AuditOperationKind.OTHER;
    }

    private boolean containsAny(String text, String... candidates) {
        if (!StringUtils.hasText(text) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && text.contains(candidate.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String parentModuleKey(String moduleKey) {
        if (!StringUtils.hasText(moduleKey)) {
            return null;
        }
        int idx = moduleKey.indexOf('.');
        return idx > 0 ? moduleKey.substring(0, idx) : null;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "platform.general";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
