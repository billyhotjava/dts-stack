package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalDirectory;
import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalItem;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDataPortalDirectoryRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDataPortalItemRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenVersionRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DataPortalService {

    public static final String CONTENT_SCREEN = "SCREEN";
    public static final String CONTENT_DASHBOARD = "DASHBOARD";
    static final int MAX_DIRECTORY_DEPTH = 6;
    static final int MAX_DIRECTORIES = 200;
    static final int MAX_ITEMS = 1_000;
    private static final int SORT_STEP = 10;

    private final AnalyticsDataPortalDirectoryRepository directoryRepository;
    private final AnalyticsDataPortalItemRepository itemRepository;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsScreenVersionRepository screenVersionRepository;
    private final AnalyticsDashboardRepository dashboardRepository;

    public DataPortalService(
            AnalyticsDataPortalDirectoryRepository directoryRepository,
            AnalyticsDataPortalItemRepository itemRepository,
            AnalyticsScreenRepository screenRepository,
            AnalyticsScreenVersionRepository screenVersionRepository,
            AnalyticsDashboardRepository dashboardRepository) {
        this.directoryRepository = directoryRepository;
        this.itemRepository = itemRepository;
        this.screenRepository = screenRepository;
        this.screenVersionRepository = screenVersionRepository;
        this.dashboardRepository = dashboardRepository;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        return new Snapshot(
                directoryRepository.findAllByArchivedFalseOrderBySortOrderAscIdAsc(),
                itemRepository.findAllByOrderBySortOrderAscIdAsc());
    }

    public AnalyticsDataPortalDirectory createDirectory(String rawName, Long parentId, Long actorId) {
        String name = validateName(rawName);
        if (actorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法识别目录创建人");
        }
        if (directoryRepository.countByArchivedFalse() >= MAX_DIRECTORIES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "门户目录数量已达到上限");
        }

        List<AnalyticsDataPortalDirectory> active = activeDirectories();
        validatePlacement(active, null, parentId);
        ensureUniqueSiblingName(active, null, parentId, name);

        AnalyticsDataPortalDirectory directory = new AnalyticsDataPortalDirectory();
        directory.setName(name);
        directory.setParentId(parentId);
        directory.setSortOrder(nextDirectorySort(active, parentId));
        directory.setArchived(false);
        directory.setCreatedBy(actorId);
        return directoryRepository.save(directory);
    }

    public AnalyticsDataPortalDirectory updateDirectory(Long directoryId, String rawName, Long parentId) {
        AnalyticsDataPortalDirectory directory = requireActiveDirectory(directoryId);
        String name = validateName(rawName);
        List<AnalyticsDataPortalDirectory> active = activeDirectories();
        validatePlacement(active, directoryId, parentId);
        ensureUniqueSiblingName(active, directoryId, parentId, name);

        if (!Objects.equals(directory.getParentId(), parentId)) {
            directory.setParentId(parentId);
            directory.setSortOrder(nextDirectorySort(active, parentId));
        }
        directory.setName(name);
        return directoryRepository.save(directory);
    }

    public void deleteDirectory(Long directoryId) {
        AnalyticsDataPortalDirectory directory = requireActiveDirectory(directoryId);
        if (directoryRepository.existsByArchivedFalseAndParentId(directoryId)
                || itemRepository.existsByDirectoryId(directoryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先移除该目录下的子目录和内容");
        }
        directory.setArchived(true);
        directoryRepository.save(directory);
    }

    public AnalyticsDataPortalItem createItem(Long directoryId, String rawContentType, Long contentId, Long actorId) {
        requireActiveDirectory(directoryId);
        if (actorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法识别内容绑定人");
        }
        if (contentId == null || contentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的大屏或看板");
        }
        if (itemRepository.count() >= MAX_ITEMS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "门户内容数量已达到上限");
        }

        String contentType = normalizeContentType(rawContentType);
        validatePublishedContent(contentType, contentId);
        if (itemRepository.existsByDirectoryIdAndContentTypeAndContentId(directoryId, contentType, contentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该目录已包含所选内容");
        }

        List<AnalyticsDataPortalItem> existing = itemRepository.findAllByOrderBySortOrderAscIdAsc();
        AnalyticsDataPortalItem item = new AnalyticsDataPortalItem();
        item.setDirectoryId(directoryId);
        item.setContentType(contentType);
        item.setContentId(contentId);
        item.setSortOrder(nextItemSort(existing, directoryId));
        item.setCreatedBy(actorId);
        return itemRepository.save(item);
    }

    public void deleteItem(Long itemId) {
        AnalyticsDataPortalItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "门户内容绑定不存在"));
        itemRepository.delete(item);
    }

    private List<AnalyticsDataPortalDirectory> activeDirectories() {
        return directoryRepository.findAllByArchivedFalseOrderBySortOrderAscIdAsc();
    }

    private AnalyticsDataPortalDirectory requireActiveDirectory(Long directoryId) {
        if (directoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择门户目录");
        }
        return directoryRepository.findById(directoryId)
                .filter(directory -> !directory.isArchived())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "门户目录不存在"));
    }

    private void validatePlacement(
            List<AnalyticsDataPortalDirectory> active,
            Long movingDirectoryId,
            Long parentId) {
        Map<Long, AnalyticsDataPortalDirectory> byId = new HashMap<>();
        for (AnalyticsDataPortalDirectory directory : active) {
            byId.put(directory.getId(), directory);
        }

        if (parentId != null && !byId.containsKey(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级目录不存在或已停用");
        }
        if (movingDirectoryId != null && Objects.equals(movingDirectoryId, parentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录不能移动到自身下面");
        }

        int parentDepth = 0;
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null) {
            if (!visited.add(cursor) || Objects.equals(cursor, movingDirectoryId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "目录移动会形成循环层级");
            }
            AnalyticsDataPortalDirectory parent = byId.get(cursor);
            if (parent == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "门户目录层级不完整");
            }
            parentDepth++;
            cursor = parent.getParentId();
        }

        int subtreeDepth = movingDirectoryId == null ? 1 : subtreeDepth(active, movingDirectoryId, new HashSet<>());
        if (parentDepth + subtreeDepth > MAX_DIRECTORY_DEPTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "门户目录最多支持六级");
        }
    }

    private int subtreeDepth(
            List<AnalyticsDataPortalDirectory> active,
            Long directoryId,
            Set<Long> path) {
        if (!path.add(directoryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "门户目录存在循环层级");
        }
        int maxChildDepth = 0;
        for (AnalyticsDataPortalDirectory candidate : active) {
            if (Objects.equals(candidate.getParentId(), directoryId)) {
                maxChildDepth = Math.max(maxChildDepth, subtreeDepth(active, candidate.getId(), path));
            }
        }
        path.remove(directoryId);
        return maxChildDepth + 1;
    }

    private void ensureUniqueSiblingName(
            List<AnalyticsDataPortalDirectory> active,
            Long currentId,
            Long parentId,
            String name) {
        for (AnalyticsDataPortalDirectory candidate : active) {
            if (!Objects.equals(candidate.getId(), currentId)
                    && Objects.equals(candidate.getParentId(), parentId)
                    && candidate.getName() != null
                    && candidate.getName().trim().equalsIgnoreCase(name)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同级目录名称已存在");
            }
        }
    }

    private void validatePublishedContent(String contentType, Long contentId) {
        if (CONTENT_SCREEN.equals(contentType)) {
            AnalyticsScreen screen = screenRepository.findById(contentId)
                    .filter(candidate -> !candidate.isArchived())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "大屏不存在或已停用"));
            if (screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅可绑定已发布的大屏");
            }
            return;
        }

        AnalyticsDashboard dashboard = dashboardRepository.findById(contentId)
                .filter(candidate -> !candidate.isArchived())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "看板不存在或已停用"));
        if (!"PUBLISHED".equals(dashboard.getLifecycleStatus())
                || dashboard.getPublishedRevisionId() == null
                || !"AVAILABLE".equals(dashboard.getRegistrationStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅可绑定已发布且可用的看板");
        }
    }

    private static String normalizeContentType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CONTENT_SCREEN.equals(normalized) && !CONTENT_DASHBOARD.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容类型仅支持大屏或看板");
        }
        return normalized;
    }

    private static String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录名称不能为空");
        }
        if (name.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录名称不能超过64个字符");
        }
        return name;
    }

    private static int nextDirectorySort(List<AnalyticsDataPortalDirectory> active, Long parentId) {
        int max = 0;
        for (AnalyticsDataPortalDirectory directory : active) {
            if (Objects.equals(directory.getParentId(), parentId) && directory.getSortOrder() != null) {
                max = Math.max(max, directory.getSortOrder());
            }
        }
        return max + SORT_STEP;
    }

    private static int nextItemSort(List<AnalyticsDataPortalItem> items, Long directoryId) {
        int max = 0;
        for (AnalyticsDataPortalItem item : items) {
            if (Objects.equals(item.getDirectoryId(), directoryId) && item.getSortOrder() != null) {
                max = Math.max(max, item.getSortOrder());
            }
        }
        return max + SORT_STEP;
    }

    public record Snapshot(
            List<AnalyticsDataPortalDirectory> directories,
            List<AnalyticsDataPortalItem> items) {}
}
