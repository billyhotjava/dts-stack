package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingGlossaryTermVersionRepository extends JpaRepository<ModelingGlossaryTermVersion, java.util.UUID> {
    Optional<ModelingGlossaryTermVersion> findByTermAndVersion(ModelingGlossaryTerm term, String version);

    List<ModelingGlossaryTermVersion> findByTermOrderByCreatedDateDesc(ModelingGlossaryTerm term);
}

