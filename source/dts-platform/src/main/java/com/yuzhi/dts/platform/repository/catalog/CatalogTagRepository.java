package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogTagRepository extends JpaRepository<CatalogTag, UUID> {
    boolean existsByCode(String code);

    Optional<CatalogTag> findByCode(String code);

    boolean existsByCategoryId(UUID categoryId);

    @Query(
        """
        select t from CatalogTag t
        where (:categoryId is null or t.categoryId = :categoryId)
          and (:enabled is null or t.enabled = :enabled)
          and (:keyword = ''
            or lower(t.code) like lower(concat('%', :keyword, '%'))
            or lower(t.name) like lower(concat('%', :keyword, '%')))
        """
    )
    Page<CatalogTag> search(
        @Param("categoryId") UUID categoryId,
        @Param("keyword") String keyword,
        @Param("enabled") Boolean enabled,
        Pageable pageable
    );

    @Query("select t.categoryId as categoryId, count(t.id) as tagCount from CatalogTag t group by t.categoryId")
    List<CategoryTagCount> countGroupedByCategoryId();

    interface CategoryTagCount {
        UUID getCategoryId();

        long getTagCount();
    }
}
