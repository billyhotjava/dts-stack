package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingSqlModelRepository extends JpaRepository<ModelingSqlModel, UUID> {
    List<ModelingSqlModel> findByPlanId(UUID planId);

    Optional<ModelingSqlModel> findFirstByModelSpecId(UUID modelSpecId);

    @Query(
        value = """
        select exists (
            select 1
              from modeling_model_implementation i
             where i.model_spec_id = :modelSpecId
               and i.status = 'ACTIVE'
               and (
                   exists (
                       select 1
                         from modeling_dbt_artifact a
                        where a.model_spec_id = i.model_spec_id
                          and a.revision = i.model_revision
                          and a.model_checksum = i.model_checksum
                          and a.ownership = i.ownership
                          and a.project_key = i.project_key
                          and a.dbt_unique_id = i.dbt_unique_id
                          and a.implementation_revision = i.implementation_revision
                   )
                   or exists (
                       select 1
                         from modeling_model_lifecycle_event e
                        where e.model_spec_id = i.model_spec_id
                          and e.model_revision = i.model_revision
                          and e.details_json ->> 'implementationRevision' = i.implementation_revision::text
                          and e.details_json ->> 'implementationChecksum' = i.current_implementation_checksum
                   )
               )
        )
        """,
        nativeQuery = true
    )
    boolean hasCurrentLifecycleEvidence(@Param("modelSpecId") UUID modelSpecId);

    Optional<ModelingSqlModel> findFirstByPlanIdAndNameIgnoreCase(UUID planId, String name);

    Optional<ModelingSqlModel> findFirstByNameIgnoreCase(String name);

    Optional<ModelingSqlModel> findFirstByAliasIgnoreCase(String alias);

    List<ModelingSqlModel> findBySourceDataSourceId(UUID sourceDataSourceId);

    List<ModelingSqlModel> findByModelPathIn(List<String> modelPaths);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM ModelingSqlModel m WHERE lower(m.tags) LIKE lower(concat('%', :tag, '%'))")
    List<ModelingSqlModel> findByTagsContainingIgnoreCase(@org.springframework.data.repository.query.Param("tag") String tag);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM ModelingSqlModel m WHERE upper(m.layer) = upper(:layer)")
    List<ModelingSqlModel> findByLayerIgnoreCase(@org.springframework.data.repository.query.Param("layer") String layer);
}
