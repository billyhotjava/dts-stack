package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingGlossaryTermReviewRepository extends JpaRepository<ModelingGlossaryTermReview, UUID> {
    List<ModelingGlossaryTermReview> findByTermOrderByCreatedDateDesc(ModelingGlossaryTerm term);
}

