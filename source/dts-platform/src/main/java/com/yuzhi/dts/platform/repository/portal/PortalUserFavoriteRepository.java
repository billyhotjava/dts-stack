package com.yuzhi.dts.platform.repository.portal;

import com.yuzhi.dts.platform.domain.portal.PortalUserFavorite;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortalUserFavoriteRepository extends JpaRepository<PortalUserFavorite, UUID> {
    List<PortalUserFavorite> findByUserLoginOrderBySortOrderAscCreatedDateDesc(String userLogin);
}
