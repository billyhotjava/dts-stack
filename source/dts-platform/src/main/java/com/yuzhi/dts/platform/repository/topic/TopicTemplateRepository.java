package com.yuzhi.dts.platform.repository.topic;

import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicTemplateRepository extends JpaRepository<TopicTemplate, UUID> {
    Optional<TopicTemplate> findByTemplateCodeIgnoreCase(String templateCode);
}
