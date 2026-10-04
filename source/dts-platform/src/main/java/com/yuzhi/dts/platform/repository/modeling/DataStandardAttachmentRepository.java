package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataStandardAttachmentRepository extends JpaRepository<DataStandardAttachment, UUID> {
    List<DataStandardAttachment> findByStandardOrderByCreatedDateDesc(DataStandard standard);
    Optional<DataStandardAttachment> findByIdAndStandard(UUID id, DataStandard standard);
}
