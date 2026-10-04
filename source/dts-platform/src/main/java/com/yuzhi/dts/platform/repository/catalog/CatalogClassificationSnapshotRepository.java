package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogClassificationSnapshotRepository extends JpaRepository<CatalogClassificationSnapshot, UUID> {
    Optional<CatalogClassificationSnapshot> findBySubjectTypeAndSubjectKey(String subjectType, String subjectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select snapshot
          from CatalogClassificationSnapshot snapshot
         where snapshot.subjectType = :subjectType
           and snapshot.subjectKey = :subjectKey
        """
    )
    Optional<CatalogClassificationSnapshot> lockBySubject(
        @Param("subjectType") String subjectType,
        @Param("subjectKey") String subjectKey
    );

    @Modifying
    @Query(
        value = """
        insert into catalog_classification_snapshot (
            id, subject_type, subject_key, asset_type,
            declared_level, detected_level, manual_floor, effective_level,
            origin_type, origin_ref, sealed_at, evidence_checksum,
            propagation_status, record_version,
            created_by, created_date, last_modified_by, last_modified_date
        ) values (
            :id, :subjectType, :subjectKey, :assetType,
            :declaredLevel, :detectedLevel, :manualFloor, :effectiveLevel,
            :originType, :originRef, :sealedAt, :evidenceChecksum,
            'PROPAGATION_PENDING', 0,
            :actor, :auditTimestamp, :actor, :auditTimestamp
        )
        on conflict (subject_type, subject_key) do nothing
        """,
        nativeQuery = true
    )
    int insertSealedIfAbsent(
        @Param("id") UUID id,
        @Param("subjectType") String subjectType,
        @Param("subjectKey") String subjectKey,
        @Param("assetType") String assetType,
        @Param("declaredLevel") String declaredLevel,
        @Param("detectedLevel") String detectedLevel,
        @Param("manualFloor") String manualFloor,
        @Param("effectiveLevel") String effectiveLevel,
        @Param("originType") String originType,
        @Param("originRef") String originRef,
        @Param("sealedAt") Instant sealedAt,
        @Param("evidenceChecksum") String evidenceChecksum,
        @Param("actor") String actor,
        @Param("auditTimestamp") Instant auditTimestamp
    );
}
