package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.CreateTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.PatchTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlIdeTabDto;
import com.yuzhi.dts.platform.service.sql.dto.UpsertTabRequest;
import java.util.List;
import java.util.UUID;

public interface SqlIdeTabService {

    int MAX_TABS_PER_USER = 30;

    /** List all tabs of the current user, ordered by sortOrder ascending. */
    List<SqlIdeTabDto> listByUser(String userLogin);

    /** Create a new tab. Throws TooManyTabsException if user already has MAX_TABS_PER_USER. */
    SqlIdeTabDto create(String userLogin, CreateTabRequest req);

    /**
     * Partial update. Throws TabConflictException if req.updatedAt() != persisted.lastModifiedDate.
     * Returns updated DTO.
     */
    SqlIdeTabDto patch(String userLogin, UUID id, PatchTabRequest req);

    /** Delete by id. Silently no-op if not found or user mismatch — does not leak existence. */
    void delete(String userLogin, UUID id);

    /** Batch upsert (for debounced sync). Max 50 entries per call. */
    List<SqlIdeTabDto> batchUpsert(String userLogin, List<UpsertTabRequest> reqs);
}
