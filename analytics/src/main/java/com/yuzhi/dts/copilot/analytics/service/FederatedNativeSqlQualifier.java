package com.yuzhi.dts.copilot.analytics.service;

@FunctionalInterface
public interface FederatedNativeSqlQualifier {

    String qualify(long databaseId, String sql);

    static FederatedNativeSqlQualifier noop() {
        return (databaseId, sql) -> sql;
    }
}
