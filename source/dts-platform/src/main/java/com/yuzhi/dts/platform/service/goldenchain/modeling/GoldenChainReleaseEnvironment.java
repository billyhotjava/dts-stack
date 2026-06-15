package com.yuzhi.dts.platform.service.goldenchain.modeling;

public enum GoldenChainReleaseEnvironment {
    PROD,
    DEV,
    DEMO;

    public boolean isProd() {
        return this == PROD;
    }
}
