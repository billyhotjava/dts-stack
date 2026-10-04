package com.yuzhi.dts.copilot.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAlert;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAlertRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAlertSubscriptionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSynonymRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.JdbcDetailsResolver;
import com.yuzhi.dts.copilot.analytics.service.MetadataSyncService;
import com.yuzhi.dts.copilot.analytics.service.PlatformInfraClient;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class DatabaseResourceTest {

    private static final SlowSuccessDriver SLOW_SUCCESS_DRIVER = new SlowSuccessDriver();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AnalyticsSessionService sessionService;

    @Mock
    private com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository databaseRepository;

    @Mock
    private com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository tableRepository;

    @Mock
    private com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository fieldRepository;

    @Mock
    private AnalyticsCardRepository cardRepository;

    @Mock
    private AnalyticsDashboardCardRepository dashboardCardRepository;

    @Mock
    private AnalyticsAlertRepository alertRepository;

    @Mock
    private AnalyticsAlertSubscriptionRepository alertSubscriptionRepository;

    @Mock
    private AnalyticsSynonymRepository synonymRepository;

    @Mock
    private MetadataSyncService metadataSyncService;

    @Mock
    private JdbcDetailsResolver jdbcDetailsResolver;

    @Mock
    private PlatformInfraClient platformInfraClient;

    @BeforeAll
    static void registerDriver() throws SQLException {
        DriverManager.registerDriver(SLOW_SUCCESS_DRIVER);
    }

    @AfterAll
    static void deregisterDriver() throws SQLException {
        DriverManager.deregisterDriver(SLOW_SUCCESS_DRIVER);
    }

    @Test
    void validateConnectionAllowsSlowButSuccessfulJdbcHandshake() throws Exception {
        DatabaseResource resource = new DatabaseResource(
                sessionService,
                databaseRepository,
                tableRepository,
                fieldRepository,
                cardRepository,
                dashboardCardRepository,
                alertRepository,
                alertSubscriptionRepository,
                synonymRepository,
                metadataSyncService,
                jdbcDetailsResolver,
                platformInfraClient,
                MAPPER);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setPasswordHash("secret");
        user.setSuperuser(true);
        user.setActive(true);
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(jdbcDetailsResolver.resolve(any(), any())).thenReturn(new JdbcDetailsResolver.JdbcDetails("jdbc:slow:test", null, null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        DatabaseResource.DatabaseRequest body = new DatabaseResource.DatabaseRequest(
                "test-db",
                "mysql",
                MAPPER.readTree("""
                        {
                          "host": "db.weitaor.com",
                          "port": 3306,
                          "database": "rs_cloud_flower",
                          "username": "flowerai",
                          "password": "ai2026Flower@demo"
                        }
                        """),
                null,
                false,
                null,
                null,
                null,
                true,
                true,
                false);

        long startedAt = System.nanoTime();
        ResponseEntity<?> response = resource.validateConnection(body, request);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of());
        assertThat(elapsedMs).isGreaterThanOrEqualTo(6_000);
    }

    @Test
    void deleteRemovesDependentMetadataBeforeDeletingDatabase() throws Exception {
        DatabaseResource resource = new DatabaseResource(
                sessionService,
                databaseRepository,
                tableRepository,
                fieldRepository,
                cardRepository,
                dashboardCardRepository,
                alertRepository,
                alertSubscriptionRepository,
                synonymRepository,
                metadataSyncService,
                jdbcDetailsResolver,
                platformInfraClient,
                MAPPER);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        user.setSuperuser(true);
        user.setActive(true);
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(databaseRepository.existsById(5L)).thenReturn(true);

        AnalyticsCard card = new AnalyticsCard();
        card.setId(21L);
        when(cardRepository.findAllByDatabaseIdOrderByIdAsc(5L)).thenReturn(List.of(card));

        AnalyticsAlert alert = new AnalyticsAlert();
        setEntityId(alert, 31L);
        alert.setCardId(21L);
        when(alertRepository.findAllByCardIdInOrderByIdAsc(List.of(21L))).thenReturn(List.of(alert));

        ResponseEntity<?> response = resource.delete(5L, new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(204);

        InOrder inOrder = inOrder(
                cardRepository,
                alertRepository,
                alertSubscriptionRepository,
                dashboardCardRepository,
                fieldRepository,
                tableRepository,
                synonymRepository,
                databaseRepository);
        inOrder.verify(cardRepository).findAllByDatabaseIdOrderByIdAsc(5L);
        inOrder.verify(alertRepository).findAllByCardIdInOrderByIdAsc(List.of(21L));
        inOrder.verify(alertSubscriptionRepository).deleteAllByAlertIdIn(List.of(31L));
        inOrder.verify(alertRepository).deleteAllByCardIdIn(List.of(21L));
        inOrder.verify(dashboardCardRepository).deleteAllByCardIdIn(List.of(21L));
        inOrder.verify(cardRepository).deleteAllByDatabaseId(5L);
        inOrder.verify(fieldRepository).deleteAllByDatabaseId(5L);
        inOrder.verify(tableRepository).deleteAllByDatabaseId(5L);
        inOrder.verify(synonymRepository).deleteAllByDatabaseId(5L);
        inOrder.verify(databaseRepository).deleteById(5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listHidesSystemRuntimePostgresDatabases() throws Exception {
        DatabaseResource resource = new DatabaseResource(
                sessionService,
                databaseRepository,
                tableRepository,
                fieldRepository,
                cardRepository,
                dashboardCardRepository,
                alertRepository,
                alertSubscriptionRepository,
                synonymRepository,
                metadataSyncService,
                jdbcDetailsResolver,
                platformInfraClient,
                MAPPER);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        user.setSuperuser(true);
        user.setActive(true);
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));

        AnalyticsDatabase copilotDatabase = new AnalyticsDatabase();
        copilotDatabase.setId(6L);
        copilotDatabase.setName("Copilot业务库");
        copilotDatabase.setEngine("postgres");
        copilotDatabase.setDetailsJson("{\"dataSourceId\":7}");

        AnalyticsDatabase runtimeDatabase = new AnalyticsDatabase();
        runtimeDatabase.setId(9L);
        runtimeDatabase.setName("园林业务库");
        runtimeDatabase.setEngine("postgres");
        runtimeDatabase.setDetailsJson("{\"dataSourceId\":10}");

        AnalyticsDatabase businessDatabase = new AnalyticsDatabase();
        businessDatabase.setId(8L);
        businessDatabase.setName("新业务测试库1");
        businessDatabase.setEngine("mysql");
        businessDatabase.setDetailsJson("{\"dataSourceId\":9}");

        when(databaseRepository.findAll()).thenReturn(List.of(copilotDatabase, businessDatabase, runtimeDatabase));
        when(platformInfraClient.fetchDataSourceDetail(7L)).thenReturn(new PlatformInfraClient.DataSourceDetail(
                "7",
                "Copilot业务库",
                "postgres",
                "jdbc:postgresql://localhost:5432/copilot",
                "copilot",
                null,
                null,
                Map.of(),
                Map.of(),
                "ACTIVE",
                null));
        when(platformInfraClient.fetchDataSourceDetail(10L)).thenReturn(new PlatformInfraClient.DataSourceDetail(
                "10",
                "园林业务库",
                "postgres",
                "jdbc:postgresql://copilot-postgres:5432/garden",
                "readonly",
                null,
                null,
                Map.of(),
                Map.of(),
                "ACTIVE",
                null));
        ResponseEntity<?> response = resource.list(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).extracting(item -> item.get("id")).containsExactly(8L);
        assertThat(data).extracting(item -> item.get("name")).containsExactly("新业务测试库1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listHidesSystemRuntimeDatabasesByExplicitRole() throws Exception {
        DatabaseResource resource = new DatabaseResource(
                sessionService,
                databaseRepository,
                tableRepository,
                fieldRepository,
                cardRepository,
                dashboardCardRepository,
                alertRepository,
                alertSubscriptionRepository,
                synonymRepository,
                metadataSyncService,
                jdbcDetailsResolver,
                platformInfraClient,
                MAPPER);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        user.setSuperuser(true);
        user.setActive(true);
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));

        AnalyticsDatabase runtimeDatabase = new AnalyticsDatabase();
        runtimeDatabase.setId(6L);
        runtimeDatabase.setName("Internal Runtime");
        runtimeDatabase.setEngine("mysql");
        runtimeDatabase.setDetailsJson("{\"dataSourceId\":7}");
        runtimeDatabase.setDatabaseRole(AnalyticsDatabaseRole.SYSTEM_RUNTIME);

        AnalyticsDatabase businessDatabase = new AnalyticsDatabase();
        businessDatabase.setId(8L);
        businessDatabase.setName("新业务测试库1");
        businessDatabase.setEngine("mysql");
        businessDatabase.setDetailsJson("{\"dataSourceId\":9}");
        businessDatabase.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_PRIMARY);

        when(databaseRepository.findAll()).thenReturn(List.of(runtimeDatabase, businessDatabase));

        ResponseEntity<?> response = resource.list(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).extracting(item -> item.get("id")).containsExactly(8L);
        assertThat(data).extracting(item -> item.get("name")).containsExactly("新业务测试库1");
    }

    @Test
    void createMarksImportedRuntimeDatabaseWithExplicitSystemRole() throws Exception {
        DatabaseResource resource = new DatabaseResource(
                sessionService,
                databaseRepository,
                tableRepository,
                fieldRepository,
                cardRepository,
                dashboardCardRepository,
                alertRepository,
                alertSubscriptionRepository,
                synonymRepository,
                metadataSyncService,
                jdbcDetailsResolver,
                platformInfraClient,
                MAPPER);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        user.setSuperuser(true);
        user.setActive(true);
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(platformInfraClient.fetchDataSourceDetail(10L)).thenReturn(new PlatformInfraClient.DataSourceDetail(
                "10",
                "园林业务库",
                "postgres",
                "jdbc:postgresql://copilot-postgres:5432/garden",
                "readonly",
                null,
                null,
                Map.of(),
                Map.of(),
                "ACTIVE",
                null));
        when(databaseRepository.save(any())).thenAnswer(invocation -> {
            AnalyticsDatabase saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        DatabaseResource.DatabaseRequest body = new DatabaseResource.DatabaseRequest(
                "园林业务库",
                "postgres",
                MAPPER.readTree("""
                        {
                          "dataSourceId": 10
                        }
                        """),
                null,
                false,
                null,
                null,
                null,
                true,
                true,
                false);

        ResponseEntity<?> response = resource.create(body, new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.mockito.ArgumentCaptor<AnalyticsDatabase> captor = org.mockito.ArgumentCaptor.forClass(AnalyticsDatabase.class);
        verify(databaseRepository).save(captor.capture());
        assertThat(captor.getValue().getDatabaseRole()).isEqualTo(AnalyticsDatabaseRole.SYSTEM_RUNTIME);
    }

    @Test
    void updatePreservesConfiguredLogicalDatasourceAliasesWhenRefreshingConnectionDetails() throws Exception {
        DatabaseResource resource = new DatabaseResource(
                sessionService,
                databaseRepository,
                tableRepository,
                fieldRepository,
                cardRepository,
                dashboardCardRepository,
                alertRepository,
                alertSubscriptionRepository,
                synonymRepository,
                metadataSyncService,
                jdbcDetailsResolver,
                platformInfraClient,
                MAPPER);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        user.setSuperuser(true);
        user.setActive(true);
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));

        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(8L);
        database.setName("DTS dbt模型库");
        database.setEngine("postgres");
        database.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_SECONDARY);
        database.setDetailsJson("""
                {
                  "dataSourceId": 17,
                  "logicalSourceAliases": ["prs.flowerbiz.federated", "prs.flowerbiz.mart"]
                }
                """);
        when(databaseRepository.findById(8L)).thenReturn(Optional.of(database));
        when(platformInfraClient.fetchDataSourceDetail(18L)).thenReturn(new PlatformInfraClient.DataSourceDetail(
                "18",
                "联邦查询入口",
                "postgres",
                "jdbc:postgresql://federated-query:5432/biadmin",
                "readonly",
                null,
                null,
                Map.of(),
                Map.of(),
                "ACTIVE",
                null));
        when(databaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DatabaseResource.DatabaseRequest body = new DatabaseResource.DatabaseRequest(
                null,
                null,
                MAPPER.readTree("""
                        {
                          "dataSourceId": 18
                        }
                        """),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        ResponseEntity<?> response = resource.update(8L, body, new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.mockito.ArgumentCaptor<AnalyticsDatabase> captor = org.mockito.ArgumentCaptor.forClass(AnalyticsDatabase.class);
        verify(databaseRepository).save(captor.capture());
        assertThat(captor.getValue().getDetailsJson())
                .contains("\"dataSourceId\":18")
                .contains("logicalSourceAliases")
                .contains("prs.flowerbiz.federated")
                .contains("prs.flowerbiz.mart");
    }

    private static void setEntityId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static final class SlowSuccessDriver implements Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            try {
                Thread.sleep(6_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while simulating a slow JDBC handshake", e);
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isValid" -> true;
                        case "isClosed" -> false;
                        case "close" -> null;
                        case "unwrap" -> null;
                        case "isWrapperFor" -> false;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:slow:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class || returnType == short.class || returnType == int.class || returnType == long.class) {
                return 0;
            }
            if (returnType == float.class || returnType == double.class) {
                return 0.0;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
