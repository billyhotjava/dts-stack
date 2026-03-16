package com.yuzhi.dts.platform.repository.topic;

import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicTemplateEntityRepository extends JpaRepository<TopicTemplateEntity, UUID> {
    List<TopicTemplateEntity> findByTemplateIdOrderByEntityCodeAsc(UUID templateId);

    Optional<TopicTemplateEntity> findFirstByTemplateIdAndEntityCodeIgnoreCase(UUID templateId, String entityCode);
}
