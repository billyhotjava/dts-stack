package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroup;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPermissionsGraph;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsGroupRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsPermissionsGraphRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for checking query permissions.
 *
 * <p>Implements permission checking for database, table, and field access.
 * Permissions are determined by group membership and the permissions graph.
 */
@Service
public class QueryPermissionService {

    private static final Logger log = LoggerFactory.getLogger(QueryPermissionService.class);

    /**
     * Permission levels for data access.
     */
    public enum PermissionLevel {
        NONE,      // No access
        LIMITED,   // Limited access (e.g., only aggregated data)
        FULL       // Full access to all data
    }

    private final AnalyticsUserRepository userRepository;
    private final AnalyticsGroupRepository groupRepository;
    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsPermissionsGraphRepository permissionsGraphRepository;
    private final ObjectMapper objectMapper;

    public QueryPermissionService(
            AnalyticsUserRepository userRepository,
            AnalyticsGroupRepository groupRepository,
            AnalyticsDatabaseRepository databaseRepository,
            AnalyticsTableRepository tableRepository,
            AnalyticsPermissionsGraphRepository permissionsGraphRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.databaseRepository = databaseRepository;
        this.tableRepository = tableRepository;
        this.permissionsGraphRepository = permissionsGraphRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Check if a user can access a database.
     *
     * @param userId the user ID
     * @param databaseId the database ID
     * @return the permission level
     */
    @Transactional(readOnly = true)
    public PermissionLevel getDatabasePermission(Long userId, long databaseId) {
        if (userId == null) {
            return PermissionLevel.NONE;
        }

        // Superusers have full access
        Optional<AnalyticsUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return PermissionLevel.NONE;
        }
        AnalyticsUser user = userOpt.get();
        if (user.isSuperuser()) {
            return PermissionLevel.FULL;
        }

        // Check if database exists
        if (!databaseRepository.existsById(databaseId)) {
            return PermissionLevel.NONE;
        }

        // Get user's groups
        Set<Long> userGroupIds = getUserGroupIds(userId);
        if (userGroupIds.isEmpty()) {
            return PermissionLevel.NONE;
        }

        // Check permissions graph for each group
        PermissionLevel highestPermission = PermissionLevel.NONE;
        for (Long groupId : userGroupIds) {
            PermissionLevel groupPermission = getGroupDatabasePermission(groupId, databaseId);
            if (groupPermission.ordinal() > highestPermission.ordinal()) {
                highestPermission = groupPermission;
            }
            if (highestPermission == PermissionLevel.FULL) {
                break;
            }
        }

        return highestPermission;
    }

    /**
     * Check if a user can access a table.
     *
     * @param userId the user ID
     * @param tableId the table ID
     * @return the permission level
     */
    @Transactional(readOnly = true)
    public PermissionLevel getTablePermission(Long userId, long tableId) {
        if (userId == null) {
            return PermissionLevel.NONE;
        }

        // Superusers have full access
        Optional<AnalyticsUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return PermissionLevel.NONE;
        }
        AnalyticsUser user = userOpt.get();
        if (user.isSuperuser()) {
            return PermissionLevel.FULL;
        }

        // Get table and database info
        Optional<AnalyticsTable> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return PermissionLevel.NONE;
        }
        AnalyticsTable table = tableOpt.get();
        Long databaseId = table.getDatabaseId();
        if (databaseId == null) {
            return PermissionLevel.NONE;
        }

        // First check database permission
        PermissionLevel dbPermission = getDatabasePermission(userId, databaseId);
        if (dbPermission == PermissionLevel.NONE) {
            return PermissionLevel.NONE;
        }
        if (dbPermission == PermissionLevel.FULL) {
            return PermissionLevel.FULL;
        }

        // Check table-specific permissions
        Set<Long> userGroupIds = getUserGroupIds(userId);
        PermissionLevel highestPermission = PermissionLevel.NONE;
        for (Long groupId : userGroupIds) {
            PermissionLevel tablePermission = getGroupTablePermission(groupId, databaseId, tableId);
            if (tablePermission.ordinal() > highestPermission.ordinal()) {
                highestPermission = tablePermission;
            }
            if (highestPermission == PermissionLevel.FULL) {
                break;
            }
        }

        return highestPermission;
    }

    /**
     * Check if a user can execute a query.
     *
     * @param userId the user ID
     * @param databaseId the database ID
     * @param query the MBQL or native query
     * @return a permission check result
     */
    @Transactional(readOnly = true)
    public QueryPermissionCheck checkQueryPermission(Long userId, long databaseId, JsonNode query) {
        // Check database permission
        PermissionLevel dbPermission = getDatabasePermission(userId, databaseId);
        if (dbPermission == PermissionLevel.NONE) {
            return QueryPermissionCheck.deny("You don't have permission to access this database");
        }

        // For native queries, any database access is sufficient.
        // Copilot-generated SQL is restricted to SELECT by the system prompt;
        // blocking regular users from executing query results hurts usability
        // without adding meaningful security (the DB user itself is read-only).
        String queryType = query != null ? query.path("type").asText("") : "";
        if ("native".equalsIgnoreCase(queryType)) {
            return QueryPermissionCheck.allow();
        }

        // For MBQL queries, check table permissions
        JsonNode mbqlQuery = query != null ? query.get("query") : null;
        if (mbqlQuery == null) {
            return QueryPermissionCheck.allow();
        }

        // Extract table ID from query
        long tableId = mbqlQuery.path("source-table").asLong(0);
        if (tableId > 0) {
            PermissionLevel tablePermission = getTablePermission(userId, tableId);
            if (tablePermission == PermissionLevel.NONE) {
                return QueryPermissionCheck.deny("You don't have permission to access this table");
            }
        }

        // Check joined tables
        JsonNode joins = mbqlQuery.get("joins");
        if (joins != null && joins.isArray()) {
            for (JsonNode join : joins) {
                long joinTableId = join.path("source-table").asLong(0);
                if (joinTableId > 0) {
                    PermissionLevel joinTablePermission = getTablePermission(userId, joinTableId);
                    if (joinTablePermission == PermissionLevel.NONE) {
                        return QueryPermissionCheck.deny("You don't have permission to access one of the joined tables");
                    }
                }
            }
        }

        return QueryPermissionCheck.allow();
    }

    /**
     * Check if a user can view a card/question.
     *
     * @param userId the user ID
     * @param cardDatabaseId the card's database ID
     * @param collectionId the card's collection ID (null for root collection)
     * @return true if the user can view the card
     */
    @Transactional(readOnly = true)
    public boolean canViewCard(Long userId, Long cardDatabaseId, Long collectionId) {
        if (userId == null) {
            return false;
        }

        // Superusers can view all cards
        Optional<AnalyticsUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        if (userOpt.get().isSuperuser()) {
            return true;
        }

        // Check database permission
        if (cardDatabaseId != null) {
            PermissionLevel dbPermission = getDatabasePermission(userId, cardDatabaseId);
            if (dbPermission == PermissionLevel.NONE) {
                return false;
            }
        }

        // TODO: Check collection permissions
        return true;
    }

    private Set<Long> getUserGroupIds(Long userId) {
        Set<Long> groupIds = new HashSet<>();
        // Add the "All Users" group (ID = 1) by default
        groupIds.add(1L);

        // Get explicit group memberships
        List<AnalyticsGroup> groups = groupRepository.findGroupsByUserId(userId);
        for (AnalyticsGroup group : groups) {
            if (group.getId() != null) {
                groupIds.add(group.getId());
            }
        }

        return groupIds;
    }

    private PermissionLevel getGroupDatabasePermission(Long groupId, long databaseId) {
        // Get the permissions graph (there's typically just one)
        List<AnalyticsPermissionsGraph> graphs = permissionsGraphRepository.findAll();
        if (graphs.isEmpty()) {
            // No permissions configured, default to limited for all users group
            if (groupId == 1L) {
                return PermissionLevel.LIMITED;
            }
            return PermissionLevel.NONE;
        }

        // Parse the graph JSON and look for this group's permissions
        for (AnalyticsPermissionsGraph graph : graphs) {
            try {
                JsonNode graphJson = objectMapper.readTree(graph.getGraphJson());
                // Metabase graph format: {"groups": {"1": {"1": {"data": "unrestricted"}}}}
                // Where first "1" is group ID, second "1" is database ID
                JsonNode groupNode = graphJson.path("groups").path(String.valueOf(groupId));
                JsonNode dbNode = groupNode.path(String.valueOf(databaseId));

                if (!dbNode.isMissingNode() && dbNode.isObject()) {
                    String dataAccess = dbNode.path("data").asText("");
                    if (!dataAccess.isEmpty()) {
                        return parsePermissionLevel(dataAccess);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse permissions graph", e);
            }
        }

        // Default: no explicit permission means no access
        // Exception: "All Users" group (ID = 1) might have default access
        if (groupId == 1L) {
            return PermissionLevel.LIMITED;
        }
        return PermissionLevel.NONE;
    }

    private PermissionLevel getGroupTablePermission(Long groupId, Long databaseId, long tableId) {
        // Get the permissions graph
        List<AnalyticsPermissionsGraph> graphs = permissionsGraphRepository.findAll();
        if (graphs.isEmpty()) {
            return getGroupDatabasePermission(groupId, databaseId);
        }

        // Parse the graph JSON and look for table-specific permissions
        for (AnalyticsPermissionsGraph graph : graphs) {
            try {
                JsonNode graphJson = objectMapper.readTree(graph.getGraphJson());
                JsonNode groupNode = graphJson.path("groups").path(String.valueOf(groupId));
                JsonNode dbNode = groupNode.path(String.valueOf(databaseId));

                if (!dbNode.isMissingNode() && dbNode.isObject()) {
                    // Check for table-specific permission
                    JsonNode tablesNode = dbNode.path("tables");
                    if (!tablesNode.isMissingNode() && tablesNode.isObject()) {
                        JsonNode tableNode = tablesNode.path(String.valueOf(tableId));
                        if (!tableNode.isMissingNode()) {
                            String tableAccess = tableNode.asText("");
                            if (!tableAccess.isEmpty()) {
                                return parsePermissionLevel(tableAccess);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse permissions graph for table", e);
            }
        }

        // No table-specific permission, inherit from database
        return getGroupDatabasePermission(groupId, databaseId);
    }

    private PermissionLevel parsePermissionLevel(String access) {
        if (access == null || access.isEmpty()) {
            return PermissionLevel.NONE;
        }
        return switch (access.toLowerCase()) {
            case "unrestricted", "all", "yes" -> PermissionLevel.FULL;
            case "limited", "granular", "segmented" -> PermissionLevel.LIMITED;
            case "block", "none", "no" -> PermissionLevel.NONE;
            default -> PermissionLevel.NONE;
        };
    }

    /**
     * Result of a query permission check.
     */
    public record QueryPermissionCheck(boolean allowed, String denialReason) {

        public static QueryPermissionCheck allow() {
            return new QueryPermissionCheck(true, null);
        }

        public static QueryPermissionCheck deny(String reason) {
            return new QueryPermissionCheck(false, reason);
        }
    }
}
