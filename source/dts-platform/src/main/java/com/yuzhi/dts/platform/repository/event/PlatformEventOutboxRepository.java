package com.yuzhi.dts.platform.repository.event;

import com.yuzhi.dts.platform.domain.event.PlatformEventOutbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformEventOutboxRepository
    extends JpaRepository<PlatformEventOutbox, UUID>, JpaSpecificationExecutor<PlatformEventOutbox> {
    Optional<PlatformEventOutbox> findByEventId(String eventId);

    List<PlatformEventOutbox> findByDispatchStatusOrderByOccurredAtAsc(String dispatchStatus, Pageable pageable);

    long countByDispatchStatus(String dispatchStatus);
}
