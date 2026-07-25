package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CatalogAssetTagRepository extends JpaRepository<CatalogAssetTag, UUID> {
    boolean existsByTagId(UUID tagId);

    boolean existsByTagIdAndAssetTypeAndAssetKey(UUID tagId, String assetType, String assetKey);

    @Modifying
    @Transactional
    @Query(
        value = """
        insert into catalog_asset_tag (
            id, tag_id, asset_type, asset_key, tagged_by, tagged_at, migration_batch,
            created_by, created_date, last_modified_by, last_modified_date
        ) values (
            :id, :tagId, :assetType, :assetKey, :actor, :taggedAt, :migrationBatch,
            :actor, :auditTimestamp, :actor, :auditTimestamp
        )
        on conflict (tag_id, asset_type, asset_key) do nothing
        """,
        nativeQuery = true
    )
    int insertIgnore(
        @Param("id") UUID id,
        @Param("tagId") UUID tagId,
        @Param("assetType") String assetType,
        @Param("assetKey") String assetKey,
        @Param("actor") String actor,
        @Param("taggedAt") Instant taggedAt,
        @Param("migrationBatch") String migrationBatch,
        @Param("auditTimestamp") Instant auditTimestamp
    );

    List<CatalogAssetTag> findByAssetTypeAndAssetKeyOrderByTaggedAtAsc(String assetType, String assetKey);

    List<CatalogAssetTag> findByAssetTypeAndAssetKeyInOrderByTaggedAtAsc(
        String assetType,
        Collection<String> assetKeys
    );

    @Query(
        """
        select assignment.assetKey
        from CatalogAssetTag assignment
        where assignment.assetType = :assetType
          and assignment.assetKey in :assetKeys
          and assignment.tagId in :tagIds
        group by assignment.assetKey
        having count(distinct assignment.tagId) = :tagCount
        order by assignment.assetKey
        """
    )
    List<String> findAssetKeysHavingAllTagsWithin(
        @Param("assetType") String assetType,
        @Param("assetKeys") Collection<String> assetKeys,
        @Param("tagIds") Collection<UUID> tagIds,
        @Param("tagCount") long tagCount
    );

    @Query(
        value = """
        select assignment.asset_type as assetType, assignment.asset_key as assetKey
          from catalog_asset_tag assignment
         where assignment.tag_id in (:tagIds)
           and (cast(:assetType as varchar) is null or assignment.asset_type = :assetType)
         group by assignment.asset_type, assignment.asset_key
        having count(distinct assignment.tag_id) = :tagCount
         order by assignment.asset_type, assignment.asset_key
        """,
        countQuery = """
        select count(*)
          from (
            select assignment.asset_type, assignment.asset_key
              from catalog_asset_tag assignment
             where assignment.tag_id in (:tagIds)
               and (cast(:assetType as varchar) is null or assignment.asset_type = :assetType)
             group by assignment.asset_type, assignment.asset_key
            having count(distinct assignment.tag_id) = :tagCount
          ) matched_assets
        """,
        nativeQuery = true
    )
    Page<AssetRefProjection> findAssetRefsHavingAllTags(
        @Param("tagIds") Collection<UUID> tagIds,
        @Param("tagCount") long tagCount,
        @Param("assetType") String assetType,
        Pageable pageable
    );

    @Query(
        value = """
        with matched_dataset as (
            select dataset.id as dataset_id,
                   assignment.asset_key as asset_key,
                   dataset.created_date as dataset_created_date
              from catalog_asset_tag assignment
              join catalog_dataset dataset
                on assignment.asset_type = 'DATASET'
               and assignment.asset_key =
                   'source:' || coalesce(dataset.source_id::text, 'unknown')
                   || '/schema:' || regexp_replace(
                       regexp_replace(
                           regexp_replace(
                               lower(trim(coalesce(nullif(trim(dataset.hive_database), ''), 'default'))),
                               '[^a-z0-9_.:-]+',
                               '_',
                               'g'
                           ),
                           '_+',
                           '_',
                           'g'
                       ),
                       '^_+|_+$',
                       '',
                       'g'
                   )
                   || '/table:' || regexp_replace(
                       regexp_replace(
                           regexp_replace(
                               lower(trim(coalesce(nullif(trim(dataset.hive_table), ''), dataset.name))),
                               '[^a-z0-9_.:-]+',
                               '_',
                               'g'
                           ),
                           '_+',
                           '_',
                           'g'
                       ),
                       '^_+|_+$',
                       '',
                       'g'
                   )
             where assignment.tag_id in (:tagIds)
               and (cast(:domainId as uuid) is null or dataset.domain_id = cast(:domainId as uuid))
               and (cast(:sourceId as uuid) is null or dataset.source_id = cast(:sourceId as uuid))
               and (
                   cast(:classification as text) is null
                   or lower(dataset.classification) = lower(cast(:classification as text))
               )
               and (
                   cast(:ownerDept as text) is null
                   or lower(dataset.owner_dept) = lower(cast(:ownerDept as text))
               )
               and (
                   cast(:warehouseLayer as text) is null
                   or lower(dataset.warehouse_layer) = lower(cast(:warehouseLayer as text))
               )
               and (
                   cast(:exposedBy as text) is null
                   or lower(dataset.exposed_by) = lower(cast(:exposedBy as text))
               )
               and (
                   cast(:datasetType as text) is null
                   or lower(dataset.type) = lower(cast(:datasetType as text))
               )
               and (
                   cast(:enabledOnly as boolean) = false
                   or dataset.enabled is null
                   or dataset.enabled = true
               )
             group by dataset.id, assignment.asset_key, dataset.created_date
            having count(distinct assignment.tag_id) = :tagCount
        )
        select matched.dataset_id as datasetId,
               case
                   when cast(:entityType as text) = 'DATASET' then matched.dataset_id
                   when cast(:entityType as text) = 'TABLE' then table_schema.id
                   when cast(:entityType as text) = 'COLUMN' then column_schema.id
               end as entityId,
               matched.asset_key as assetKey
          from matched_dataset matched
          left join catalog_table_schema table_schema
            on cast(:entityType as text) in ('TABLE', 'COLUMN')
           and table_schema.dataset_id = matched.dataset_id
          left join catalog_column_schema column_schema
            on cast(:entityType as text) = 'COLUMN'
           and column_schema.table_id = table_schema.id
         where (
             cast(:entityType as text) = 'DATASET'
             or (cast(:entityType as text) = 'TABLE' and table_schema.id is not null)
             or (cast(:entityType as text) = 'COLUMN' and column_schema.id is not null)
         )
         order by matched.dataset_created_date desc nulls last,
                  matched.dataset_id,
                  table_schema.id,
                  column_schema.id
        """,
        nativeQuery = true
    )
    Slice<CatalogSearchCandidateProjection> findCatalogSearchCandidatesHavingAllTags(
        @Param("tagIds") Collection<UUID> tagIds,
        @Param("tagCount") long tagCount,
        @Param("entityType") String entityType,
        @Param("domainId") UUID domainId,
        @Param("sourceId") UUID sourceId,
        @Param("classification") String classification,
        @Param("ownerDept") String ownerDept,
        @Param("warehouseLayer") String warehouseLayer,
        @Param("exposedBy") String exposedBy,
        @Param("datasetType") String datasetType,
        @Param("enabledOnly") boolean enabledOnly,
        Pageable pageable
    );

    @Query(
        """
        select assignment.tagId as tagId, count(assignment.id) as usageCount
        from CatalogAssetTag assignment
        where assignment.tagId in :tagIds
        group by assignment.tagId
        """
    )
    List<TagUsageCount> countGroupedByTagId(@Param("tagIds") Collection<UUID> tagIds);

    long countByTagId(UUID tagId);

    long deleteByAssetTypeAndAssetKeyAndTagIdIn(String assetType, String assetKey, Collection<UUID> tagIds);

    void deleteByTagId(UUID tagId);

    interface AssetRefProjection {
        String getAssetType();

        String getAssetKey();
    }

    interface CatalogSearchCandidateProjection {
        UUID getDatasetId();

        UUID getEntityId();

        String getAssetKey();
    }

    interface TagUsageCount {
        UUID getTagId();

        long getUsageCount();
    }
}
