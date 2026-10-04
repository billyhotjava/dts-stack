package com.yuzhi.dts.platform.service.workbench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference;
import com.yuzhi.dts.platform.repository.workbench.WorkbenchUserPreferenceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchComponentDescriptor;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchPreferenceItem;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchPreferencesRequest;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchPreferencesResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkbenchPreferencesService {

    private static final int VERSION = 1;

    private static final List<ComponentDefinition> COMPONENTS = List.of(
        new ComponentDefinition("leader-kpi", "概览指标", "查看平台核心指标和使用趋势", Set.of()),
        new ComponentDefinition("top-reports", "常用大屏", "快速访问常用报表和大屏", Set.of()),
        new ComponentDefinition("core-assets", "核心资产", "查看重点数据资产入口", Set.of()),
        new ComponentDefinition("screen-strip", "已发布大屏", "查看已发布的大屏成果", Set.of()),
        new ComponentDefinition("todo", "待办事项", "查看审批、阻断和异常处理项", Set.of()),
        new ComponentDefinition("bi-delivery", "BI 与大屏成果", "查看 BI 看板、大屏和分析成果", Set.of()),
        new ComponentDefinition("data-sources", "数据源接入", "配置和查看数据源接入状态", maintainerRoles()),
        new ComponentDefinition("golden-chain", "数据交付链路", "查看数据从接入到消费的交付链路", maintainerRoles()),
        new ComponentDefinition("governance-blockers", "治理阻断", "查看质量、权限和发布门禁阻断", maintainerRoles()),
        new ComponentDefinition("api-services", "数据 API 服务", "查看和发布数据 API 服务", maintainerRoles()),
        new ComponentDefinition("ops-health", "运行健康", "查看调度、运行和异常状态", maintainerRoles())
    );

    private final WorkbenchUserPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    public WorkbenchPreferencesService(WorkbenchUserPreferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WorkbenchPreferencesResponse getPreferences(String username, List<String> roles) {
        List<ComponentDefinition> available = availableDefinitions(roles);
        List<WorkbenchPreferenceItem> items = repository
            .findByUsername(normalizeUsername(username))
            .map(WorkbenchUserPreference::getLayoutJson)
            .map(this::readItems)
            .orElseGet(() -> defaultItems(available));
        return response(available, normalizeItems(items, available));
    }

    public WorkbenchPreferencesResponse savePreferences(
        String username,
        List<String> roles,
        WorkbenchPreferencesRequest request
    ) {
        List<ComponentDefinition> available = availableDefinitions(roles);
        List<WorkbenchPreferenceItem> normalized = normalizeItems(request == null ? List.of() : request.items(), available);
        String login = normalizeUsername(username);
        WorkbenchUserPreference entity = repository.findByUsername(login).orElseGet(WorkbenchUserPreference::new);
        entity.setUsername(login);
        entity.setVersion(VERSION);
        entity.setLayoutJson(writeItems(normalized));
        repository.save(entity);
        return response(available, normalized);
    }

    public WorkbenchPreferencesResponse resetPreferences(String username, List<String> roles) {
        String login = normalizeUsername(username);
        repository.findByUsername(login).ifPresent(repository::delete);
        List<ComponentDefinition> available = availableDefinitions(roles);
        return response(available, defaultItems(available));
    }

    private List<ComponentDefinition> availableDefinitions(List<String> roles) {
        Set<String> normalizedRoles = roles == null
            ? Set.of()
            : roles
                .stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return COMPONENTS
            .stream()
            .filter(component -> component.requiredRoles().isEmpty() || component.requiredRoles().stream().anyMatch(normalizedRoles::contains))
            .toList();
    }

    private List<WorkbenchPreferenceItem> defaultItems(List<ComponentDefinition> available) {
        List<WorkbenchPreferenceItem> items = new ArrayList<>();
        for (int i = 0; i < available.size(); i++) {
            items.add(new WorkbenchPreferenceItem(available.get(i).key(), true, (i + 1) * 10));
        }
        return items;
    }

    private List<WorkbenchPreferenceItem> normalizeItems(
        List<WorkbenchPreferenceItem> rawItems,
        List<ComponentDefinition> available
    ) {
        Map<String, ComponentDefinition> availableByKey = available
            .stream()
            .collect(Collectors.toMap(ComponentDefinition::key, item -> item, (a, b) -> a, LinkedHashMap::new));
        Map<String, WorkbenchPreferenceItem> byKey = new LinkedHashMap<>();
        for (WorkbenchPreferenceItem raw : rawItems == null ? List.<WorkbenchPreferenceItem>of() : rawItems) {
            if (raw == null || raw.key() == null || raw.key().isBlank()) {
                continue;
            }
            String key = raw.key().trim();
            if (!availableByKey.containsKey(key)) {
                throw new IllegalArgumentException("unknown component key: " + key);
            }
            byKey.putIfAbsent(key, new WorkbenchPreferenceItem(key, raw.visible(), raw.order()));
        }
        List<WorkbenchPreferenceItem> sorted = byKey
            .values()
            .stream()
            .sorted(Comparator.comparingInt(WorkbenchPreferenceItem::order).thenComparing(WorkbenchPreferenceItem::key))
            .toList();
        List<WorkbenchPreferenceItem> normalized = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            WorkbenchPreferenceItem item = sorted.get(i);
            normalized.add(new WorkbenchPreferenceItem(item.key(), item.visible(), (i + 1) * 10));
        }
        return normalized;
    }

    private WorkbenchPreferencesResponse response(List<ComponentDefinition> available, List<WorkbenchPreferenceItem> items) {
        List<WorkbenchComponentDescriptor> descriptors = available
            .stream()
            .map(component -> new WorkbenchComponentDescriptor(component.key(), component.title(), component.description(), true, null))
            .toList();
        return new WorkbenchPreferencesResponse(VERSION, descriptors, items);
    }

    private List<WorkbenchPreferenceItem> readItems(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return List.of();
        }
        try {
            StoredLayout layout = objectMapper.readValue(layoutJson, StoredLayout.class);
            return layout.items() == null ? List.of() : layout.items();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String writeItems(List<WorkbenchPreferenceItem> items) {
        try {
            return objectMapper.writeValueAsString(new StoredLayout(VERSION, items));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize workbench preferences", e);
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        return username.trim();
    }

    private static Set<String> maintainerRoles() {
        return Set.of(
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.OP_ADMIN,
            AuthoritiesConstants.INST_DATA_OWNER,
            AuthoritiesConstants.DEPT_DATA_OWNER,
            AuthoritiesConstants.INST_LEADER,
            AuthoritiesConstants.DEPT_LEADER
        );
    }

    private record ComponentDefinition(String key, String title, String description, Set<String> requiredRoles) {}

    private record StoredLayout(int version, List<WorkbenchPreferenceItem> items) {}
}
