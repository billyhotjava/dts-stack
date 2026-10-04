package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingTemplate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingTemplateRepository extends JpaRepository<ModelingTemplate, UUID> {}

