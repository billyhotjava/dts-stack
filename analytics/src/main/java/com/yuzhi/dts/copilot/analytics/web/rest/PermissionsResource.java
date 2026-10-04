package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroup;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroupMembership;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPermissionsGraph;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsGroupMembershipRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsGroupRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsPermissionsGraphRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
@Transactional
public class PermissionsResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsUserRepository userRepository;
    private final AnalyticsGroupRepository groupRepository;
    private final AnalyticsGroupMembershipRepository membershipRepository;
    private final AnalyticsPermissionsGraphRepository graphRepository;
    private final GroupService groupService;
    private final ObjectMapper objectMapper;

    public PermissionsResource(
            AnalyticsSessionService sessionService,
            AnalyticsUserRepository userRepository,
            AnalyticsGroupRepository groupRepository,
            AnalyticsGroupMembershipRepository membershipRepository,
            AnalyticsPermissionsGraphRepository graphRepository,
            GroupService groupService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.graphRepository = graphRepository;
        this.groupService = groupService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/group", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> groups(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        groupService.ensureSystemGroupsExist();

        List<AnalyticsGroup> groups = groupRepository.findAll();
        groups.sort(Comparator.comparingLong(g -> g.getId() == null ? Long.MAX_VALUE : g.getId()));

        List<Map<String, Object>> response = new ArrayList<>();
        for (AnalyticsGroup group : groups) {
            long memberCount = membershipRepository.countByGroupId(group.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", group.getId());
            item.put("name", group.getName());
            item.put("member_count", memberCount);
            response.add(item);
        }
        response.sort((a, b) -> {
            long aId = ((Number) a.get("id")).longValue();
            long bId = ((Number) b.get("id")).longValue();
            if (aId == GroupService.ADMIN_GROUP_ID && bId != GroupService.ADMIN_GROUP_ID) {
                return -1;
            }
            if (bId == GroupService.ADMIN_GROUP_ID && aId != GroupService.ADMIN_GROUP_ID) {
                return 1;
            }
            return Long.compare(aId, bId);
        });
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/group/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> groupDetails(@PathVariable("id") int id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return groupRepository
                .findById((long) id)
                .<ResponseEntity<?>>map(group -> ResponseEntity.ok(Map.of("id", group.getId(), "name", group.getName())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(path = "/group", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createGroup(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        String name = body == null ? null : Optional.ofNullable(body.get("name")).map(JsonNode::asText).map(String::trim).orElse(null);
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
        }
        if (groupRepository.findByNameIgnoreCase(name).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "name already in use.")));
        }
        AnalyticsGroup group = new AnalyticsGroup();
        group.setName(name);
        group.setGroupType("custom");
        group = groupRepository.save(group);
        return ResponseEntity.ok(Map.of("id", group.getId(), "name", group.getName()));
    }

    @PutMapping(path = "/group/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateGroup(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsGroup> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsGroup group = existing.get();
        if ("system".equalsIgnoreCase(group.getGroupType())) {
            return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("Cannot update system groups.");
        }
        String name = body == null ? null : Optional.ofNullable(body.get("name")).map(JsonNode::asText).map(String::trim).orElse(null);
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
        }
        Long currentId = group.getId();
        if (groupRepository.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(currentId)).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "name already in use.")));
        }
        group.setName(name);
        group = groupRepository.save(group);
        return ResponseEntity.ok(Map.of("id", group.getId(), "name", group.getName()));
    }

    @DeleteMapping(path = "/group/{id}")
    public ResponseEntity<?> deleteGroup(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsGroup> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsGroup group = existing.get();
        if ("system".equalsIgnoreCase(group.getGroupType()) || id == GroupService.ALL_USERS_GROUP_ID || id == GroupService.ADMIN_GROUP_ID) {
            return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("Cannot delete system groups.");
        }
        groupRepository.delete(group);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/membership", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> memberships(@RequestParam(name = "group_id", required = false) Long groupId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        List<AnalyticsGroupMembership> memberships = membershipRepository.findAllFiltered(groupId);
        List<Map<String, Object>> response = new ArrayList<>();
        for (AnalyticsGroupMembership membership : memberships) {
            response.add(toMetabaseMembership(membership));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/membership", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMembership(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Long groupId = body == null ? null : Optional.ofNullable(body.get("group_id")).filter(JsonNode::canConvertToLong).map(JsonNode::asLong).orElse(null);
        Long userId = body == null ? null : Optional.ofNullable(body.get("user_id")).filter(JsonNode::canConvertToLong).map(JsonNode::asLong).orElse(null);
        boolean isGroupManager = body != null && Optional.ofNullable(body.get("is_group_manager")).map(JsonNode::asBoolean).orElse(false);
        if (groupId == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("group_id", "value must be a valid group id.")));
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("user_id", "value must be a valid user id.")));
        }
        if (groupRepository.findById(groupId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("group_id", "Group not found.")));
        }
        Optional<AnalyticsUser> memberUser = userRepository.findById(userId);
        if (memberUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("user_id", "User not found.")));
        }

        AnalyticsGroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId).orElseGet(AnalyticsGroupMembership::new);
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setGroupManager(isGroupManager);
        membership = membershipRepository.save(membership);

        if (groupId == GroupService.ADMIN_GROUP_ID && !memberUser.get().isSuperuser()) {
            AnalyticsUser target = memberUser.get();
            target.setSuperuser(true);
            userRepository.save(target);
            groupService.ensureUserInDefaultGroups(target);
        }

        return ResponseEntity.ok(toMetabaseMembership(membership));
    }

    @GetMapping(path = "/membership/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> membershipDetails(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return membershipRepository
                .findById(id)
                .<ResponseEntity<?>>map(membership -> ResponseEntity.ok(toMetabaseMembership(membership)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/membership/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateMembership(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsGroupMembership> existing = membershipRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsGroupMembership membership = existing.get();
        boolean isGroupManager = body != null && Optional.ofNullable(body.get("is_group_manager")).map(JsonNode::asBoolean).orElse(membership.isGroupManager());
        membership.setGroupManager(isGroupManager);
        membership = membershipRepository.save(membership);
        return ResponseEntity.ok(toMetabaseMembership(membership));
    }

    @DeleteMapping(path = "/membership/{id}")
    public ResponseEntity<?> deleteMembership(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsGroupMembership> existing = membershipRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsGroupMembership membership = existing.get();
        if (membership.getGroupId().equals(GroupService.ALL_USERS_GROUP_ID)) {
            return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("Cannot remove All Users membership.");
        }
        if (membership.getGroupId().equals(GroupService.ADMIN_GROUP_ID)) {
            Optional<AnalyticsUser> memberUser = userRepository.findById(membership.getUserId());
            if (memberUser.isPresent() && memberUser.get().isSuperuser()) {
                AnalyticsUser target = memberUser.get();
                target.setSuperuser(false);
                userRepository.save(target);
            }
        }
        membershipRepository.delete(membership);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> graph(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        groupService.ensureSystemGroupsExist();

        AnalyticsPermissionsGraph stored = graphRepository.findById(1L).orElseGet(() -> {
            AnalyticsPermissionsGraph graph = new AnalyticsPermissionsGraph();
            graph.setId(1L);
            graph.setRevision(0);
            graph.setGraphJson("{\"revision\":0,\"groups\":{}}");
            return graphRepository.save(graph);
        });

        JsonNode node;
        try {
            node = objectMapper.readTree(stored.getGraphJson());
        } catch (Exception e) {
            node = objectMapper.createObjectNode();
        }
        ObjectNode object = node != null && node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
        object.put("revision", stored.getRevision());
        ObjectNode groups = object.has("groups") && object.get("groups").isObject() ? (ObjectNode) object.get("groups") : objectMapper.createObjectNode();
        object.set("groups", groups);
        for (AnalyticsGroup group : groupRepository.findAll()) {
            String key = String.valueOf(group.getId());
            if (!groups.has(key)) {
                groups.set(key, objectMapper.createObjectNode());
            }
        }
        return ResponseEntity.ok(object);
    }

    @PutMapping(path = "/graph", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putGraph(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        AnalyticsPermissionsGraph stored = graphRepository.findById(1L).orElseGet(() -> {
            AnalyticsPermissionsGraph graph = new AnalyticsPermissionsGraph();
            graph.setId(1L);
            graph.setRevision(0);
            graph.setGraphJson("{\"revision\":0,\"groups\":{}}");
            return graphRepository.save(graph);
        });

        int newRevision = stored.getRevision() + 1;
        ObjectNode updated = body != null && body.isObject() ? (ObjectNode) body.deepCopy() : objectMapper.createObjectNode();
        updated.put("revision", newRevision);
        if (!updated.has("groups") || !updated.get("groups").isObject()) {
            updated.set("groups", objectMapper.createObjectNode());
        }
        stored.setRevision(newRevision);
        stored.setGraphJson(updated.toString());
        graphRepository.save(stored);
        return ResponseEntity.ok(updated);
    }

    private static Map<String, Object> toMetabaseMembership(AnalyticsGroupMembership membership) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", membership.getId());
        item.put("group_id", membership.getGroupId());
        item.put("user_id", membership.getUserId());
        item.put("is_group_manager", membership.isGroupManager());
        return item;
    }
}
