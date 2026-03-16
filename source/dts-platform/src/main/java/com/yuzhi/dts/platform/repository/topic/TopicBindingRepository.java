package com.yuzhi.dts.platform.repository.topic;

import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicBindingRepository extends JpaRepository<TopicBinding, UUID> {
    Optional<TopicBinding> findByTemplateIdAndEntityIdAndScopeKey(UUID templateId, UUID entityId, String scopeKey);
}
