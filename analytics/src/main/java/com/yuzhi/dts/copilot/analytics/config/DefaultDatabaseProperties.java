package com.yuzhi.dts.copilot.analytics.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.analytics")
public record DefaultDatabaseProperties(List<DatabaseEntry> defaultDatabases) {

    public DefaultDatabaseProperties {
        if (defaultDatabases == null) {
            defaultDatabases = List.of();
        }
    }

    public record DatabaseEntry(
            String name,
            String role,
            String engine,
            String host,
            int port,
            String db,
            String user,
            String password,
            boolean autoSyncMetadata) {

        public DatabaseEntry {
            if (engine == null || engine.isBlank()) {
                engine = "postgres";
            }
            if (port <= 0) {
                port = 5432;
            }
        }
    }
}
