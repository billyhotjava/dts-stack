package com.yuzhi.dts.platform.service.etl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtReleaseGateService {

    private static final Duration DEFAULT_MAX_BUILD_AGE = Duration.ofHours(24);

    private final DbtRunResultService dbtRunResultService;
    private final boolean requireGitMetadata;

    public DbtReleaseGateService(
        DbtRunResultService dbtRunResultService,
        @Value("${dts.dbt.release-gate.require-git-metadata:false}") boolean requireGitMetadata
    ) {
        this.dbtRunResultService = dbtRunResultService;
        this.requireGitMetadata = requireGitMetadata;
    }

    public DbtReleaseGateResult evaluate(String selector, String gitRef, String commitSha, Boolean strictMode) {
        boolean strict = strictMode == null || strictMode;
        String normalizedSelector = normalizeText(selector);
        String normalizedGitRef = normalizeText(gitRef);
        String normalizedCommitSha = normalizeText(commitSha);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        evaluateGitBranch(normalizedGitRef, strict, blockers, warnings);
        evaluateCommitSha(normalizedCommitSha, strict, blockers, warnings);

        DbtRunResultService.DbtRunSummary latestRun = dbtRunResultService.loadLatestBuildSummary(20);
        BuildEvidence evidence = null;
        if (latestRun == null || !latestRun.present()) {
            blockers.add("未发现可用构建记录，请先执行 dbt compile/test/build");
        } else {
            evidence = new BuildEvidence(
                latestRun.invocationId(),
                latestRun.command(),
                latestRun.status(),
                latestRun.generatedAt(),
                latestRun.runResultsPath()
            );
            evaluateBuildEvidence(latestRun, normalizedSelector, strict, blockers, warnings);
        }

        return new DbtReleaseGateResult(
            normalizedSelector,
            strict,
            normalizedGitRef,
            normalizedCommitSha,
            blockers.isEmpty() ? (warnings.isEmpty() ? "PASS" : "WARN") : "BLOCK",
            !blockers.isEmpty(),
            !warnings.isEmpty(),
            blockers,
            warnings,
            evidence
        );
    }

    private void evaluateGitBranch(String gitRef, boolean strict, List<String> blockers, List<String> warnings) {
        if (!StringUtils.hasText(gitRef)) {
            if (requireGitMetadata && strict) {
                blockers.add("缺少 Git 分支信息（gitRef）");
            } else if (requireGitMetadata) {
                warnings.add("缺少 Git 分支信息（gitRef）");
            }
            return;
        }
        if (!isAllowedReleaseBranch(gitRef)) {
            blockers.add("分支不符合发布策略，仅允许 main/master/release/*/hotfix/*，当前: " + gitRef);
        }
    }

    private void evaluateCommitSha(String commitSha, boolean strict, List<String> blockers, List<String> warnings) {
        if (!StringUtils.hasText(commitSha)) {
            if (requireGitMetadata && strict) {
                blockers.add("缺少 Commit SHA（commitSha）");
            } else if (requireGitMetadata) {
                warnings.add("缺少 Commit SHA（commitSha）");
            }
            return;
        }
        String normalized = commitSha.trim();
        if (!normalized.matches("^[0-9a-fA-F]{7,40}$")) {
            if (strict) {
                blockers.add("Commit SHA 格式非法，需为 7-40 位十六进制");
            } else {
                warnings.add("Commit SHA 格式非法，需为 7-40 位十六进制");
            }
        }
    }

    private void evaluateBuildEvidence(
        DbtRunResultService.DbtRunSummary summary,
        String selector,
        boolean strict,
        List<String> blockers,
        List<String> warnings
    ) {
        String status = normalizeUpper(summary.status());
        if (!"SUCCESS".equals(status)) {
            blockers.add("最近一次构建状态为 " + defaultText(status, "UNKNOWN") + "，不允许发布");
        }
        if (!isAllowedBuildCommand(summary.command())) {
            blockers.add("最近一次构建命令不是 compile/test/build，请先补齐 CI 校验");
        }
        Instant generatedAt = parseInstant(summary.generatedAt());
        if (generatedAt == null) {
            if (strict) {
                blockers.add("构建记录缺少生成时间，无法确认有效性");
            } else {
                warnings.add("构建记录缺少生成时间，建议重跑 compile/test");
            }
        } else {
            Duration age = Duration.between(generatedAt, Instant.now());
            if (age.compareTo(DEFAULT_MAX_BUILD_AGE) > 0) {
                String msg =
                    "构建记录已过期（" + age.toHours() + "h），需在 " + DEFAULT_MAX_BUILD_AGE.toHours() + "h 内完成 compile/test/build";
                if (strict) {
                    blockers.add(msg);
                } else {
                    warnings.add(msg);
                }
            }
        }
        if (StringUtils.hasText(selector) && !"all".equalsIgnoreCase(selector.trim()) && !commandContainsSelector(summary.command())) {
            warnings.add("构建命令未显式包含 selector，建议用相同 selector 重新执行 compile/test");
        }
    }

    private boolean commandContainsSelector(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.contains(" --select ") || normalized.contains(" --models ");
    }

    private boolean isAllowedBuildCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        return (
            "compile".equals(normalized) ||
            "test".equals(normalized) ||
            "build".equals(normalized) ||
            normalized.startsWith("compile ") ||
            normalized.startsWith("test ") ||
            normalized.startsWith("build ") ||
            normalized.startsWith("dbt compile") ||
            normalized.startsWith("dbt test") ||
            normalized.startsWith("dbt build") ||
            normalized.contains(" dbt compile") ||
            normalized.contains(" dbt test") ||
            normalized.contains(" dbt build")
        );
    }

    private boolean isAllowedReleaseBranch(String gitRef) {
        String normalized = gitRef.trim().toLowerCase(Locale.ROOT);
        return (
            "main".equals(normalized) ||
            "master".equals(normalized) ||
            normalized.startsWith("release/") ||
            normalized.startsWith("hotfix/")
        );
    }

    private Instant parseInstant(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeUpper(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record BuildEvidence(
        String invocationId,
        String command,
        String status,
        String generatedAt,
        String runResultsPath
    ) {}

    public record DbtReleaseGateResult(
        String selector,
        boolean strictMode,
        String gitRef,
        String commitSha,
        String decision,
        boolean blocking,
        boolean warning,
        List<String> blockers,
        List<String> warnings,
        BuildEvidence buildEvidence
    ) {}
}
