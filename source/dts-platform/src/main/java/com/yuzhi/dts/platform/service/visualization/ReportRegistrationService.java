package com.yuzhi.dts.platform.service.visualization;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ReportRegistrationService {

    private static final Pattern ASSET_KEY = Pattern.compile("[A-Za-z0-9_-]{1,96}");
    private static final Set<String> CLASSIFICATIONS = Set.of(
        "DATA_PUBLIC", "DATA_INTERNAL", "DATA_CONFIDENTIAL", "DATA_SENSITIVE", "DATA_SECRET"
    );

    private final BiReportLinkRepository repository;

    public ReportRegistrationService(BiReportLinkRepository repository) {
        this.repository = repository;
    }

    public RegistrationResult register(ReportRegistrationCommand command) {
        ReportRegistrationCommand value = validate(command);
        BiReportLink link = repository
            .findByEngineAndAssetTypeAndAssetKey(value.engine(), value.assetType(), value.assetKey())
            .orElse(null);
        if (link != null && link.getAssetVersion() != null && value.assetVersion() < link.getAssetVersion()) {
            return result(link, "STALE_IGNORED");
        }
        if (link == null) {
            link = new BiReportLink();
            link.setCode(stableCode(value.assetType(), value.assetKey()));
            link.setEngine(value.engine());
            link.setAssetType(value.assetType());
            link.setAssetKey(value.assetKey());
            link.setSortOrder(0);
        }
        link.setAssetVersion(value.assetVersion());
        link.setTitle(value.title());
        link.setReportType(value.reportType());
        link.setUrl(value.url());
        link.setQueryDatasetId(value.queryDatasetId());
        link.setQueryDatasetVersion(value.queryDatasetVersion());
        link.setDeptCodes(csv(value.deptCodes()));
        link.setRoleCodes(csv(value.roleCodes()));
        link.setClassification(value.classification());
        link.setExpiresAt(value.expiresAt());
        link.setEnabled(value.enabled());
        link.setReconcileStatus("SYNCED");
        return result(repository.save(link), "SYNCED");
    }

    private ReportRegistrationCommand validate(ReportRegistrationCommand command) {
        if (command == null) throw invalid("registration command is required");
        String engine = upper(command.engine());
        String assetType = upper(command.assetType());
        String assetKey = text(command.assetKey());
        String title = text(command.title());
        String reportType = upper(command.reportType());
        String url = text(command.url());
        String classification = normalizeClassification(command.classification());
        List<String> departments = codes(command.deptCodes());
        List<String> roles = codes(command.roleCodes());
        if (!"DTS_BI".equals(engine)) throw invalid("engine must be DTS_BI");
        if (!Set.of("ANALYSIS", "DASHBOARD").contains(assetType)) throw invalid("assetType is unsupported");
        if (!ASSET_KEY.matcher(assetKey).matches()) throw invalid("assetKey is invalid");
        if (command.assetVersion() < 1) throw invalid("assetVersion must be positive");
        if (!StringUtils.hasText(title) || title.length() > 256) throw invalid("title is invalid");
        if (!Set.of("ANALYSIS", "DASHBOARD").contains(reportType)) throw invalid("reportType is unsupported");
        if (!StringUtils.hasText(url) || !url.startsWith("/bi/") || url.length() > 1024) throw invalid("url must be an internal BI path");
        if (!CLASSIFICATIONS.contains(classification)) throw invalid("classification is unsupported");
        if (departments.isEmpty() && roles.isEmpty()) throw invalid("at least one department or role is required");
        if (command.expiresAt() != null && command.expiresAt().isBefore(Instant.now())) throw invalid("expiresAt is in the past");
        return new ReportRegistrationCommand(
            engine, assetType, assetKey, command.assetVersion(), title, reportType, url,
            command.queryDatasetId(), command.queryDatasetVersion(), departments, roles,
            classification, command.expiresAt(), command.enabled()
        );
    }

    private RegistrationResult result(BiReportLink link, String status) {
        return new RegistrationResult(
            link.getId() == null ? null : link.getId().toString(),
            link.getAssetVersion() == null ? 0L : link.getAssetVersion(),
            status
        );
    }

    private String stableCode(String assetType, String assetKey) {
        return "dts-bi-" + assetType.toLowerCase(Locale.ROOT) + "-" + assetKey;
    }

    private List<String> codes(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String code = text(value);
            if (StringUtils.hasText(code) && code.length() <= 128) normalized.add(code);
        }
        return List.copyOf(normalized);
    }

    private String csv(List<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String normalizeClassification(String value) {
        String normalized = upper(value);
        return switch (normalized) {
            case "PUBLIC", "S0", "DATA_PUBLIC" -> "DATA_PUBLIC";
            case "INTERNAL", "S1", "DATA_INTERNAL" -> "DATA_INTERNAL";
            case "CONFIDENTIAL", "S2", "DATA_CONFIDENTIAL" -> "DATA_CONFIDENTIAL";
            case "SENSITIVE", "S3", "DATA_SENSITIVE" -> "DATA_SENSITIVE";
            case "SECRET", "S4", "DATA_SECRET" -> "DATA_SECRET";
            default -> normalized;
        };
    }

    private String upper(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record ReportRegistrationCommand(
        String engine,
        String assetType,
        String assetKey,
        long assetVersion,
        String title,
        String reportType,
        String url,
        UUID queryDatasetId,
        Integer queryDatasetVersion,
        List<String> deptCodes,
        List<String> roleCodes,
        String classification,
        Instant expiresAt,
        boolean enabled
    ) {}

    public record RegistrationResult(String registrationId, long assetVersion, String reconcileStatus) {}
}
