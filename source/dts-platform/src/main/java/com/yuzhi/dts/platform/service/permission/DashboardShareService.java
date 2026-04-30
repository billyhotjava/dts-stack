package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.permission.dto.AssetGrantDto;
import com.yuzhi.dts.platform.service.permission.dto.DashboardShareRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 大屏共享服务。封装授予 / 撤销 / 列出 grant 的业务逻辑：
 * - 授予 / 撤销前由 DashboardAccessGuard 决定 caller 是否有权限
 * - 所有成功 / 拒绝结果都写审计（AuditService）
 * - 严格策略 1：MANAGE 不能再传递（仅 owner / superAdmin 能授 MANAGE）
 *
 * 注：本服务不发邮件 / 通知，仅负责数据层 + 审计。前端 UI 由 Step 3 接入。
 */
@Service
@Transactional
public class DashboardShareService {

    private static final String ASSET_TYPE = DashboardAccessGuard.ASSET_TYPE;
    private static final String GRANTEE_USER = DashboardAccessGuard.GRANTEE_USER;
    private static final String PERM_VIEW = DashboardAccessGuard.PERM_VIEW;
    private static final String PERM_MANAGE = DashboardAccessGuard.PERM_MANAGE;

    private final BiReportLinkRepository reportRepo;
    private final AssetGrantRepository grantRepo;
    private final DashboardAccessGuard accessGuard;
    private final DashboardCallerResolver callerResolver;
    private final AuditService audit;

    public DashboardShareService(
        BiReportLinkRepository reportRepo,
        AssetGrantRepository grantRepo,
        DashboardAccessGuard accessGuard,
        DashboardCallerResolver callerResolver,
        AuditService audit
    ) {
        this.reportRepo = reportRepo;
        this.grantRepo = grantRepo;
        this.accessGuard = accessGuard;
        this.callerResolver = callerResolver;
        this.audit = audit;
    }

    public AssetGrantDto share(UUID reportId, DashboardShareRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request body required");
        }
        BiReportLink report = reportRepo.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("not_found"));
        DashboardAccessGuard.Caller caller = callerResolver.current();
        String permission = normalizePermission(req.permission());

        // Guard 决定 caller 是否能授予 targetPermission；策略 1 在 Guard 内部生效。
        if (!accessGuard.canGrant(report, caller, permission)) {
            audit.auditFailure(
                "GRANT",
                "vis.dashboard.share",
                report.getCode(),
                "permission=" + permission + "; reason=DENIED"
            );
            throw new AccessDeniedException("not authorized to grant on dashboard " + report.getCode());
        }

        String grantee = trimToNull(req.granteeId());
        if (grantee == null) {
            throw new IllegalArgumentException("granteeId required");
        }
        // 给自己授权无业务意义且会污染审计；显式禁止。
        if (caller.username() != null && caller.username().equalsIgnoreCase(grantee)) {
            throw new IllegalArgumentException("cannot grant to yourself");
        }
        // levelOverride 只对 VIEW 有意义（MANAGE 本来就豁免密级）；忽略 caller 误传。
        boolean levelOverride = req.levelOverride() && PERM_VIEW.equals(permission);

        AssetGrant grant = new AssetGrant();
        grant.setAssetType(ASSET_TYPE);
        grant.setAssetId(report.getCode());
        grant.setGranteeType(GRANTEE_USER);
        grant.setGranteeId(grantee);
        grant.setPermission(permission);
        grant.setLevelOverride(levelOverride);
        grant.setGrantedBy(caller.username() != null ? caller.username() : "system");
        grant.setGrantReason(trimToNull(req.reason()));
        AssetGrant saved = grantRepo.save(grant);

        audit.auditAction(
            "VIS_DASHBOARD_SHARE_GRANT",
            AuditStage.SUCCESS,
            report.getCode()
                + "; grantee=" + grantee
                + "; permission=" + permission
                + (levelOverride ? "; levelOverride=true" : ""),
            null
        );
        return AssetGrantDto.from(saved);
    }

    public void revoke(UUID reportId, Long grantId) {
        BiReportLink report = reportRepo.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("not_found"));
        AssetGrant target = grantRepo.findById(grantId)
            .orElseThrow(() -> new IllegalArgumentException("grant_not_found"));

        // 防御：grant 必须真的属于该 dashboard，避免跨大屏越权撤销。
        if (
            !ASSET_TYPE.equals(target.getAssetType())
            || target.getAssetId() == null
            || !target.getAssetId().equalsIgnoreCase(report.getCode())
        ) {
            throw new IllegalArgumentException("grant does not belong to dashboard " + report.getCode());
        }

        DashboardAccessGuard.Caller caller = callerResolver.current();
        if (!accessGuard.canRevoke(report, caller, target)) {
            audit.auditFailure(
                "REVOKE",
                "vis.dashboard.share",
                report.getCode(),
                "grantId=" + grantId + "; reason=DENIED"
            );
            throw new AccessDeniedException("not authorized to revoke grant " + grantId);
        }

        String granteeBefore = target.getGranteeId();
        String permissionBefore = target.getPermission();
        grantRepo.delete(target);

        audit.auditAction(
            "VIS_DASHBOARD_SHARE_REVOKE",
            AuditStage.SUCCESS,
            report.getCode()
                + "; grantId=" + grantId
                + "; grantee=" + granteeBefore
                + "; permission=" + permissionBefore,
            null
        );
    }

    @Transactional(readOnly = true)
    public List<AssetGrantDto> listGrants(UUID reportId) {
        BiReportLink report = reportRepo.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("not_found"));
        DashboardAccessGuard.Caller caller = callerResolver.current();
        // 列出 grant 视为 MANAGE 操作的一部分。
        if (!accessGuard.canManage(report, caller)) {
            audit.auditFailure(
                "READ",
                "vis.dashboard.share",
                report.getCode(),
                "reason=DENIED_MANAGE"
            );
            throw new AccessDeniedException("not authorized to view grants on dashboard " + report.getCode());
        }
        return grantRepo.findByAssetTypeAndAssetId(ASSET_TYPE, report.getCode())
            .stream()
            .map(AssetGrantDto::from)
            .toList();
    }

    private String normalizePermission(String raw) {
        String upper = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!PERM_VIEW.equals(upper) && !PERM_MANAGE.equals(upper)) {
            throw new IllegalArgumentException("permission must be VIEW or MANAGE");
        }
        return upper;
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
