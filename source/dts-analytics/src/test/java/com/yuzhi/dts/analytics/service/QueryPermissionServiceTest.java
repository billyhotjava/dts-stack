package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsGroup;
import com.yuzhi.dts.analytics.domain.AnalyticsPermissionsGraph;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsGroupRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsPermissionsGraphRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueryPermissionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getDatabasePermission_returnsNoneWhenUserMissing() {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsGroupRepository groupRepository = mock(AnalyticsGroupRepository.class);
        AnalyticsDatabaseRepository databaseRepository = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsPermissionsGraphRepository permissionsGraphRepository = mock(AnalyticsPermissionsGraphRepository.class);

        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        QueryPermissionService service = service(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository);

        assertThat(service.getDatabasePermission(9L, 10L))
                .isEqualTo(QueryPermissionService.PermissionLevel.NONE);
    }

    @Test
    void getDatabasePermission_defaultsLimitedForAllUsersWhenNoGraph() {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsGroupRepository groupRepository = mock(AnalyticsGroupRepository.class);
        AnalyticsDatabaseRepository databaseRepository = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsPermissionsGraphRepository permissionsGraphRepository = mock(AnalyticsPermissionsGraphRepository.class);

        AnalyticsUser user = user(7L, false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(databaseRepository.existsById(10L)).thenReturn(true);
        when(groupRepository.findGroupsByUserId(7L)).thenReturn(List.of());
        when(permissionsGraphRepository.findAll()).thenReturn(List.of());

        QueryPermissionService service = service(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository);

        assertThat(service.getDatabasePermission(7L, 10L))
                .isEqualTo(QueryPermissionService.PermissionLevel.LIMITED);
    }

    @Test
    void getDatabasePermission_usesGraphPermissionsForUserGroups() {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsGroupRepository groupRepository = mock(AnalyticsGroupRepository.class);
        AnalyticsDatabaseRepository databaseRepository = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsPermissionsGraphRepository permissionsGraphRepository = mock(AnalyticsPermissionsGraphRepository.class);

        AnalyticsUser user = user(11L, false);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(databaseRepository.existsById(10L)).thenReturn(true);
        when(groupRepository.findGroupsByUserId(11L)).thenReturn(List.of(group(2L)));
        when(permissionsGraphRepository.findAll()).thenReturn(List.of(graph("""
                {
                  "groups": {
                    "2": {
                      "10": { "data": "unrestricted" }
                    }
                  }
                }
                """)));

        QueryPermissionService service = service(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository);

        assertThat(service.getDatabasePermission(11L, 10L))
                .isEqualTo(QueryPermissionService.PermissionLevel.FULL);
    }

    @Test
    void getTablePermission_usesTableSpecificPermissions() {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsGroupRepository groupRepository = mock(AnalyticsGroupRepository.class);
        AnalyticsDatabaseRepository databaseRepository = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsPermissionsGraphRepository permissionsGraphRepository = mock(AnalyticsPermissionsGraphRepository.class);

        AnalyticsUser user = user(12L, false);
        AnalyticsTable table = table(100L, 10L);

        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(databaseRepository.existsById(10L)).thenReturn(true);
        when(tableRepository.findById(100L)).thenReturn(Optional.of(table));
        when(groupRepository.findGroupsByUserId(12L)).thenReturn(List.of(group(2L)));
        when(permissionsGraphRepository.findAll()).thenReturn(List.of(graph("""
                {
                  "groups": {
                    "2": {
                      "10": {
                        "data": "limited",
                        "tables": {
                          "100": "unrestricted"
                        }
                      }
                    }
                  }
                }
                """)));

        QueryPermissionService service = service(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository);

        assertThat(service.getTablePermission(12L, 100L))
                .isEqualTo(QueryPermissionService.PermissionLevel.FULL);
    }

    @Test
    void checkQueryPermission_deniesNativeQueryWithoutFullAccess() throws Exception {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsGroupRepository groupRepository = mock(AnalyticsGroupRepository.class);
        AnalyticsDatabaseRepository databaseRepository = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsPermissionsGraphRepository permissionsGraphRepository = mock(AnalyticsPermissionsGraphRepository.class);

        AnalyticsUser user = user(20L, false);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(databaseRepository.existsById(10L)).thenReturn(true);
        when(groupRepository.findGroupsByUserId(20L)).thenReturn(List.of());
        when(permissionsGraphRepository.findAll()).thenReturn(List.of());

        QueryPermissionService service = service(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository);

        JsonNode query = objectMapper.readTree("{\"type\":\"native\"}");

        QueryPermissionService.QueryPermissionCheck result =
                service.checkQueryPermission(20L, 10L, query);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).contains("Native queries require full database access");
    }

    @Test
    void checkQueryPermission_deniesWhenJoinTableBlocked() throws Exception {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsGroupRepository groupRepository = mock(AnalyticsGroupRepository.class);
        AnalyticsDatabaseRepository databaseRepository = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tableRepository = mock(AnalyticsTableRepository.class);
        AnalyticsPermissionsGraphRepository permissionsGraphRepository = mock(AnalyticsPermissionsGraphRepository.class);

        AnalyticsUser user = user(21L, false);
        AnalyticsTable source = table(100L, 10L);
        AnalyticsTable join = table(200L, 10L);

        when(userRepository.findById(21L)).thenReturn(Optional.of(user));
        when(databaseRepository.existsById(10L)).thenReturn(true);
        when(tableRepository.findById(100L)).thenReturn(Optional.of(source));
        when(tableRepository.findById(200L)).thenReturn(Optional.of(join));
        when(groupRepository.findGroupsByUserId(21L)).thenReturn(List.of(group(2L)));
        when(permissionsGraphRepository.findAll()).thenReturn(List.of(graph("""
                {
                  "groups": {
                    "1": {
                      "10": {
                        "data": "limited",
                        "tables": {
                          "100": "unrestricted",
                          "200": "block"
                        }
                      }
                    },
                    "2": {
                      "10": {
                        "data": "limited",
                        "tables": {
                          "100": "unrestricted",
                          "200": "block"
                        }
                      }
                    }
                  }
                }
                """)));

        QueryPermissionService service = service(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository);

        JsonNode query = objectMapper.readTree("""
                {
                  "query": {
                    "source-table": 100,
                    "joins": [
                      { "source-table": 200 }
                    ]
                  }
                }
                """);

        QueryPermissionService.QueryPermissionCheck result =
                service.checkQueryPermission(21L, 10L, query);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).contains("joined tables");
    }

    private QueryPermissionService service(
            AnalyticsUserRepository userRepository,
            AnalyticsGroupRepository groupRepository,
            AnalyticsDatabaseRepository databaseRepository,
            AnalyticsTableRepository tableRepository,
            AnalyticsPermissionsGraphRepository permissionsGraphRepository) {
        return new QueryPermissionService(
                userRepository,
                groupRepository,
                databaseRepository,
                tableRepository,
                permissionsGraphRepository,
                objectMapper);
    }

    private AnalyticsUser user(Long id, boolean superuser) {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(id);
        user.setSuperuser(superuser);
        return user;
    }

    private AnalyticsGroup group(Long id) {
        AnalyticsGroup group = new AnalyticsGroup();
        group.setId(id);
        group.setName("group-" + id);
        group.setGroupType("regular");
        return group;
    }

    private AnalyticsTable table(Long id, Long databaseId) {
        AnalyticsTable table = new AnalyticsTable();
        table.setId(id);
        table.setDatabaseId(databaseId);
        table.setSchemaName("public");
        table.setName("table_" + id);
        return table;
    }

    private AnalyticsPermissionsGraph graph(String json) {
        AnalyticsPermissionsGraph graph = new AnalyticsPermissionsGraph();
        graph.setId(1L);
        graph.setRevision(1);
        graph.setGraphJson(json);
        return graph;
    }
}
