package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter.AssignmentKey;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRefPage;
import com.yuzhi.dts.platform.service.catalog.dto.AssetTagMutationResult;
import com.yuzhi.dts.platform.service.catalog.dto.AssetTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagResult;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CatalogAssetTagService {

    public static final int MAX_BATCH_ASSETS = 500;
    public static final int MAX_MUTATION_TAG_IDS = AssetTagRequest.MAX_TAG_IDS;
    public static final int MAX_SEARCH_TAG_IDS = 50;
    public static final int MAX_SEARCH_PAGE_SIZE = 100;
    public static final int MAX_EXACT_SEARCH_CANDIDATES = 5_000;
    private static final int EXACT_SEARCH_SCAN_BATCH_SIZE = 500;

    private static final Logger LOG = LoggerFactory.getLogger(CatalogAssetTagService.class);

    private final CatalogTagRepository tagRepository;
    private final CatalogAssetTagRepository assetTagRepository;
    private final CatalogAssetTagBatchWriter assetTagBatchWriter;

    public CatalogAssetTagService(
        CatalogTagRepository tagRepository,
        CatalogAssetTagRepository assetTagRepository,
        CatalogAssetTagBatchWriter assetTagBatchWriter
    ) {
        this.tagRepository = tagRepository;
        this.assetTagRepository = assetTagRepository;
        this.assetTagBatchWriter = assetTagBatchWriter;
    }

    @Transactional(readOnly = true)
    public List<CatalogTagDto> listAssetTags(String assetType, String assetKey) {
        AssetRef normalized = normalizeAsset(new AssetRef(assetType, assetKey));
        return listAssetTags(List.of(normalized)).getOrDefault(normalized, List.of());
    }

    @Transactional(readOnly = true)
    public Map<AssetRef, List<CatalogTagDto>> listAssetTags(Collection<AssetRef> refs) {
        LinkedHashSet<AssetRef> normalizedRefs = new LinkedHashSet<>();
        if (refs != null) {
            refs.stream().map(this::normalizeAsset).forEach(normalizedRefs::add);
        }
        if (normalizedRefs.isEmpty()) {
            return Map.of();
        }
        Map<String, List<AssetRef>> refsByType = new LinkedHashMap<>();
        for (AssetRef ref : normalizedRefs) {
            refsByType.computeIfAbsent(ref.assetType(), ignored -> new ArrayList<>()).add(ref);
        }
        Map<AssetRef, List<UUID>> tagIdsByRef = new LinkedHashMap<>();
        normalizedRefs.forEach(ref -> tagIdsByRef.put(ref, new ArrayList<>()));
        LinkedHashSet<UUID> tagIds = new LinkedHashSet<>();
        for (Map.Entry<String, List<AssetRef>> entry : refsByType.entrySet()) {
            List<String> assetKeys = entry.getValue().stream().map(AssetRef::assetKey).toList();
            for (CatalogAssetTag assignment : assetTagRepository.findByAssetTypeAndAssetKeyInOrderByTaggedAtAsc(
                entry.getKey(),
                assetKeys
            )) {
                AssetRef ref = new AssetRef(assignment.getAssetType(), assignment.getAssetKey());
                List<UUID> refTagIds = tagIdsByRef.get(ref);
                if (refTagIds == null) {
                    continue;
                }
                refTagIds.add(assignment.getTagId());
                tagIds.add(assignment.getTagId());
            }
        }
        if (tagIds.isEmpty()) {
            return tagIdsByRef.keySet().stream().collect(
                java.util.stream.Collectors.toMap(
                    ref -> ref,
                    ref -> List.of(),
                    (left, right) -> left,
                    LinkedHashMap::new
                )
            );
        }
        List<UUID> orderedTagIds = List.copyOf(tagIds);
        Map<UUID, CatalogTag> tagsById = new LinkedHashMap<>();
        tagRepository.findAllById(orderedTagIds).forEach(tag -> tagsById.put(tag.getId(), tag));
        Map<UUID, Long> usageCounts = usageCounts(orderedTagIds);
        Map<AssetRef, List<CatalogTagDto>> result = new LinkedHashMap<>();
        for (Map.Entry<AssetRef, List<UUID>> entry : tagIdsByRef.entrySet()) {
            List<CatalogTagDto> tags = entry
                .getValue()
                .stream()
                .map(tagId -> {
                    CatalogTag tag = tagsById.get(tagId);
                    if (tag == null) {
                        LOG.warn(
                            "catalog_asset_tag references missing tag definition assetType={} assetKey={} tagId={}",
                            entry.getKey().assetType(),
                            entry.getKey().assetKey(),
                            tagId
                        );
                    }
                    return tag;
                })
                .filter(Objects::nonNull)
                .map(tag -> toDto(tag, usageCounts.getOrDefault(tag.getId(), 0L)))
                .toList();
            result.put(entry.getKey(), tags);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Set<String> findMatchingAssetKeysWithin(
        String assetType,
        Collection<String> candidateAssetKeys,
        List<UUID> tagIds
    ) {
        String normalizedType;
        try {
            normalizedType = CatalogAssetType.from(assetType).name();
        } catch (IllegalArgumentException exception) {
            throw badRequest("资产类型不合法：" + assetType);
        }
        LinkedHashSet<String> normalizedCandidateKeys = new LinkedHashSet<>();
        if (candidateAssetKeys != null) {
            for (String candidateAssetKey : candidateAssetKeys) {
                normalizedCandidateKeys.add(
                    normalizeAsset(
                        new AssetRef(normalizedType, candidateAssetKey)
                    ).assetKey()
                );
            }
        }
        if (
            normalizedCandidateKeys.size() > MAX_EXACT_SEARCH_CANDIDATES
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "精确标签检索候选超过 5000，请增加基础筛选条件"
            );
        }
        List<UUID> normalizedTagIds = requireSearchTagIds(tagIds);
        if (normalizedCandidateKeys.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(
            assetTagRepository.findAssetKeysHavingAllTagsWithin(
                normalizedType,
                List.copyOf(normalizedCandidateKeys),
                normalizedTagIds,
                normalizedTagIds.size()
            )
        );
    }

    @Transactional(readOnly = true)
    public AssetRefPage searchAssets(
        List<UUID> tagIds,
        String assetType,
        int page,
        int size,
        Function<List<AssetRef>, List<AssetRef>> readFilter
    ) {
        if (page < 0) {
            throw badRequest("页码 page 不能小于 0");
        }
        if (size < 1 || size > MAX_SEARCH_PAGE_SIZE) {
            throw badRequest("每页数量 size 必须在 1 到 100 之间");
        }
        List<UUID> normalizedTagIds = requireSearchTagIds(tagIds);
        String normalizedType = null;
        if (StringUtils.hasText(assetType)) {
            try {
                normalizedType = CatalogAssetType.from(assetType).name();
            } catch (IllegalArgumentException exception) {
                throw badRequest("资产类型不合法：" + assetType);
            }
        }
        if (readFilter == null) {
            throw new IllegalArgumentException("readFilter is required");
        }

        long requestedOffset = (long) page * size;
        long requestedEnd = requestedOffset + size;
        long visibleTotal = 0;
        List<AssetRef> content = new ArrayList<>(size);
        int scanPage = 0;
        while (true) {
            Page<CatalogAssetTagRepository.AssetRefProjection> candidates = assetTagRepository.findAssetRefsHavingAllTags(
                normalizedTagIds,
                normalizedTagIds.size(),
                normalizedType,
                PageRequest.of(scanPage, EXACT_SEARCH_SCAN_BATCH_SIZE)
            );
            if (candidates.getTotalElements() > MAX_EXACT_SEARCH_CANDIDATES) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "精确标签检索候选超过 5000，请增加 assetType 或缩小标签范围"
                );
            }
            List<AssetRef> candidateRefs = candidates
                .getContent()
                .stream()
                .map(row -> new AssetRef(row.getAssetType(), row.getAssetKey()))
                .toList();
            Set<AssetRef> readableRefs = new LinkedHashSet<>(readFilter.apply(candidateRefs));
            for (AssetRef candidate : candidateRefs) {
                if (!readableRefs.contains(candidate)) {
                    continue;
                }
                if (visibleTotal >= requestedOffset && visibleTotal < requestedEnd) {
                    content.add(candidate);
                }
                visibleTotal++;
            }
            if (!candidates.hasNext()) {
                break;
            }
            scanPage++;
        }
        return new AssetRefPage(content, visibleTotal, page, size);
    }

    public AssetTagMutationResult tagAsset(String assetType, String assetKey, List<UUID> tagIds, String actor) {
        AssetRef asset = normalizeAsset(new AssetRef(assetType, assetKey));
        List<CatalogTag> tags = requireEnabledTags(tagIds);
        String normalizedActor = normalizeActor(actor);
        int created = 0;
        int skipped = 0;
        for (CatalogTag tag : tags) {
            int inserted = insertAssignment(asset, tag.getId(), normalizedActor);
            created += inserted;
            skipped += 1 - inserted;
        }
        return new AssetTagMutationResult(created, skipped, 0);
    }

    public AssetTagMutationResult untagAsset(String assetType, String assetKey, List<UUID> tagIds) {
        AssetRef asset = normalizeAsset(new AssetRef(assetType, assetKey));
        Set<UUID> uniqueTagIds = requireMutationTagIds(tagIds);
        long removed = assetTagRepository.deleteByAssetTypeAndAssetKeyAndTagIdIn(asset.assetType(), asset.assetKey(), uniqueTagIds);
        return new AssetTagMutationResult(0, 0, Math.toIntExact(removed));
    }

    public BatchAssetTagResult batchTag(BatchAssetTagRequest request, String actor) {
        if (request == null || request.assets() == null || request.assets().isEmpty()) {
            throw badRequest("批量打标资产不能为空");
        }
        if (request.assets().size() > MAX_BATCH_ASSETS) {
            throw badRequest("单次批量打标最多支持 500 个资产");
        }
        List<AssetRef> assets = request.assets().stream().map(this::normalizeAsset).toList();
        List<CatalogTag> tags = requireEnabledTags(request.tagIds());
        String normalizedActor = normalizeActor(actor);
        List<AssignmentKey> assignments = new ArrayList<>(assets.size() * tags.size());
        Set<AssignmentKey> uniqueAssignments = new LinkedHashSet<>();
        for (AssetRef asset : assets) {
            for (CatalogTag tag : tags) {
                AssignmentKey assignment = new AssignmentKey(asset.assetType(), asset.assetKey(), tag.getId());
                if (uniqueAssignments.add(assignment)) {
                    assignments.add(assignment);
                }
            }
        }
        Set<AssignmentKey> insertedAssignments = assetTagBatchWriter.insertIgnore(
            assignments,
            normalizedActor,
            Instant.now()
        );
        Set<AssignmentKey> creditedAssignments = new LinkedHashSet<>();
        List<BatchAssetTagResult.AssetResult> results = new ArrayList<>(assets.size());
        int created = 0;
        int skipped = 0;
        for (AssetRef asset : assets) {
            int assetCreated = 0;
            int assetSkipped = 0;
            for (CatalogTag tag : tags) {
                AssignmentKey assignment = new AssignmentKey(asset.assetType(), asset.assetKey(), tag.getId());
                int inserted = insertedAssignments.contains(assignment) && creditedAssignments.add(assignment) ? 1 : 0;
                assetCreated += inserted;
                assetSkipped += 1 - inserted;
            }
            created += assetCreated;
            skipped += assetSkipped;
            results.add(new BatchAssetTagResult.AssetResult(asset.assetType(), asset.assetKey(), assetCreated, assetSkipped));
        }
        return new BatchAssetTagResult(assets.size(), created, skipped, results);
    }

    private int insertAssignment(AssetRef asset, UUID tagId, String actor) {
        Instant now = Instant.now();
        return assetTagRepository.insertIgnore(
            UUID.randomUUID(),
            tagId,
            asset.assetType(),
            asset.assetKey(),
            actor,
            now,
            null,
            now
        );
    }

    private String normalizeActor(String actor) {
        String normalized = StringUtils.hasText(actor) ? actor.trim() : "system";
        if (normalized.length() > 50) {
            throw badRequest("操作人标识长度不能超过 50");
        }
        return normalized;
    }

    private AssetRef normalizeAsset(AssetRef asset) {
        if (asset == null) {
            throw badRequest("资产不能为空");
        }
        CatalogAssetType type;
        try {
            type = CatalogAssetType.from(asset.assetType());
        } catch (IllegalArgumentException exception) {
            throw badRequest("资产类型不合法：" + asset.assetType());
        }
        if (!StringUtils.hasText(asset.assetKey())) {
            throw badRequest("资产标识不能为空");
        }
        String assetKey = asset.assetKey().trim();
        if (assetKey.length() > 512) {
            throw badRequest("资产标识长度不能超过 512");
        }
        return new AssetRef(type.name(), assetKey);
    }

    private List<CatalogTag> requireEnabledTags(List<UUID> tagIds) {
        List<UUID> orderedIds = List.copyOf(requireMutationTagIds(tagIds));
        Map<UUID, CatalogTag> tagsById = new LinkedHashMap<>();
        tagRepository.findAllById(orderedIds).forEach(tag -> tagsById.put(tag.getId(), tag));
        List<CatalogTag> tags = new ArrayList<>(orderedIds.size());
        for (UUID tagId : orderedIds) {
            CatalogTag tag = tagsById.get(tagId);
            if (tag == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在：" + tagId);
            }
            if (!tag.isEnabled()) {
                throw badRequest("标签已停用，不能新增打标：" + tag.getName());
            }
            tags.add(tag);
        }
        return tags;
    }

    private Set<UUID> requireMutationTagIds(List<UUID> tagIds) {
        if (tagIds != null && tagIds.size() > MAX_MUTATION_TAG_IDS) {
            throw badRequest("单次资产标签操作最多支持 100 个标签");
        }
        return requireTagIds(tagIds);
    }

    private Set<UUID> requireTagIds(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty() || tagIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw badRequest("标签不能为空");
        }
        return new LinkedHashSet<>(tagIds);
    }

    private List<UUID> requireSearchTagIds(List<UUID> tagIds) {
        Set<UUID> uniqueTagIds = requireTagIds(tagIds);
        if (uniqueTagIds.size() > MAX_SEARCH_TAG_IDS) {
            throw badRequest("精确标签筛选最多支持 50 个不同标签");
        }
        return List.copyOf(uniqueTagIds);
    }

    private Map<UUID, Long> usageCounts(List<UUID> tagIds) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (CatalogAssetTagRepository.TagUsageCount count : assetTagRepository.countGroupedByTagId(tagIds)) {
            result.put(count.getTagId(), count.getUsageCount());
        }
        return result;
    }

    private CatalogTagDto toDto(CatalogTag tag, long usageCount) {
        return new CatalogTagDto(
            tag.getId(),
            tag.getCategoryId(),
            tag.getCode(),
            tag.getName(),
            tag.getColor(),
            tag.isBuiltin(),
            tag.isEnabled(),
            tag.getDescription(),
            usageCount
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
