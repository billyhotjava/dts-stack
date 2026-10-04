package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingWordRoot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingWordRootRepository extends JpaRepository<ModelingWordRoot, UUID>, JpaSpecificationExecutor<ModelingWordRoot> {}
