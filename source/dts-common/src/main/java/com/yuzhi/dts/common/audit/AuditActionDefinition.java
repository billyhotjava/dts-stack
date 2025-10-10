package com.yuzhi.dts.common.audit;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class AuditActionDefinition {

    private final String code;
    private final String display;
    private final String moduleKey;
    private final String moduleTitle;
    private final String entryKey;
    private final String entryTitle;
    private final boolean supportsFlow;
    private final EnumSet<AuditStage> phases;

    public AuditActionDefinition(
        String code,
        String display,
        String moduleKey,
        String moduleTitle,
        String entryKey,
        String entryTitle,
        boolean supportsFlow,
        Set<AuditStage> phases
    ) {
        this.code = Objects.requireNonNull(code, "code");
        this.display = Objects.requireNonNull(display, "display");
        this.moduleKey = Objects.requireNonNull(moduleKey, "moduleKey");
        this.moduleTitle = moduleTitle;
        this.entryKey = Objects.requireNonNull(entryKey, "entryKey");
        this.entryTitle = entryTitle;
        this.supportsFlow = supportsFlow;
        this.phases = phases == null || phases.isEmpty() ? EnumSet.of(AuditStage.SUCCESS) : EnumSet.copyOf(phases);
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public String getModuleTitle() {
        return moduleTitle;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public String getEntryTitle() {
        return entryTitle;
    }

    public boolean isSupportsFlow() {
        return supportsFlow;
    }

    public Set<AuditStage> getPhases() {
        return Collections.unmodifiableSet(phases);
    }

    public boolean isStageSupported(AuditStage stage) {
        return phases.contains(stage);
    }
}
