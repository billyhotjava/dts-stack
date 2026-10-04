package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingTemplate;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingTemplateVersionRepository extends JpaRepository<ModelingTemplateVersion, UUID> {
    Optional<ModelingTemplateVersion> findByTemplateAndVersion(ModelingTemplate template, String version);

    List<ModelingTemplateVersion> findByTemplateOrderByCreatedDateDesc(ModelingTemplate template);
}

