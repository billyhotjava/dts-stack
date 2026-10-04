package com.yuzhi.dts.platform.service.goldenchain.modeling;

public record GoldenChainModelSemanticContract(
    String primaryKey,
    boolean standardCodesMapped,
    String grain,
    boolean grainConsistent,
    boolean permissionConsumable
) {
    public GoldenChainModelSemanticContract {
        primaryKey = normalize(primaryKey);
        grain = normalize(grain);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
