package com.yuzhi.dts.platform.service.goldenchain.governance;

public record GoldenChainAssetIdentityCandidate(
    String sourceSystem,
    String assetType,
    String platformAssetId,
    String openMetadataFqn,
    String codeAssetKey,
    String legacyFallback
) {
    public GoldenChainAssetIdentityCandidate {
        sourceSystem = normalize(sourceSystem);
        assetType = normalize(assetType);
        platformAssetId = normalize(platformAssetId);
        openMetadataFqn = normalize(openMetadataFqn);
        codeAssetKey = normalize(codeAssetKey);
        legacyFallback = normalize(legacyFallback);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
