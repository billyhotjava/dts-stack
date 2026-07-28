package com.yuzhi.dts.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hazelcast.core.HazelcastInstance;
import com.yuzhi.dts.platform.service.sql.dto.TableInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterProperties;

class CacheConfigurationIsolationTest {

    @Test
    void standaloneCacheKeepsPlatformOutOfOtherServiceClusters() {
        CacheConfiguration configuration = new CacheConfiguration(
            new MockEnvironment(),
            new ServerProperties(),
            mock(DiscoveryClient.class)
        );

        HazelcastInstance instance = configuration.hazelcastInstance(new JHipsterProperties());
        try {
            assertThat(instance.getConfig().getClusterName()).isEqualTo("dtsPlatform");
            assertThat(instance.getConfig().getNetworkConfig().getJoin().getAutoDetectionConfig().isEnabled()).isFalse();
            assertThat(instance.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled()).isFalse();
            assertThat(instance.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled()).isFalse();
            assertThat(instance.getConfig().getMapConfig("sqlIdeTables").getTimeToLiveSeconds()).isEqualTo(300);
            assertThat(instance.getConfig().getMapConfig("sqlIdeTables").getBackupCount()).isZero();

            List<TableInfo> tables = List.of(new TableInfo("public", "ods_risk_info_v2", "TABLE", null));
            instance.<String, List<TableInfo>>getMap("sqlIdeTables").put("metadata:tables:test", tables);
            assertThat(instance.<String, List<TableInfo>>getMap("sqlIdeTables").get("metadata:tables:test"))
                .containsExactlyElementsOf(tables);
        } finally {
            instance.shutdown();
        }
    }
}
