package com.yuzhi.dts.platform.service.goldenchain.governance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GoldenChainAssetIdentityResolver {

    public GoldenChainAssetIdentityResolution resolve(List<GoldenChainAssetIdentityCandidate> candidates) {
        Set<String> resolvedKeys = new LinkedHashSet<>();
        for (GoldenChainAssetIdentityCandidate candidate : candidates == null ? List.<GoldenChainAssetIdentityCandidate>of() : candidates) {
            String key = resolveCandidateKey(candidate);
            if (hasText(key)) {
                resolvedKeys.add(key);
            }
        }
        if (resolvedKeys.isEmpty()) {
            return new GoldenChainAssetIdentityResolution(
                GoldenChainGovernanceState.PENDING_GOVERNANCE,
                "",
                List.of("无法解析资产身份：缺少 platformAssetId、OpenMetadata FQN、code asset key 和 legacy fallback")
            );
        }
        if (resolvedKeys.size() > 1) {
            return new GoldenChainAssetIdentityResolution(
                GoldenChainGovernanceState.PENDING_GOVERNANCE,
                "",
                List.of("重复资产身份：候选资产解析到多个 key，需人工确认后再合并或绑定")
            );
        }
        return new GoldenChainAssetIdentityResolution(GoldenChainGovernanceState.READY, resolvedKeys.iterator().next(), List.of());
    }

    private String resolveCandidateKey(GoldenChainAssetIdentityCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        if (hasText(candidate.platformAssetId())) {
            return "platform:" + candidate.platformAssetId();
        }
        if (hasText(candidate.openMetadataFqn())) {
            return "openmetadata:" + candidate.openMetadataFqn();
        }
        if (hasText(candidate.codeAssetKey())) {
            return "code:" + candidate.codeAssetKey();
        }
        if (hasText(candidate.legacyFallback())) {
            return "legacy:" + candidate.legacyFallback();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
