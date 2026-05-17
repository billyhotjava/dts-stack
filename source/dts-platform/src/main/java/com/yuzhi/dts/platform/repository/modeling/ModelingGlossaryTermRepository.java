package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingGlossaryTermRepository extends JpaRepository<ModelingGlossaryTerm, UUID> {
    @Query("select term from ModelingGlossaryTerm term where lower(term.code) in :codes")
    List<ModelingGlossaryTerm> findByCodeLowerIn(@Param("codes") Collection<String> codes);
}
