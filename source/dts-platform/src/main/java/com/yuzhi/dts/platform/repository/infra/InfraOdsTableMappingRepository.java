package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraOdsTableMappingRepository extends JpaRepository<InfraOdsTableMapping, UUID> {
    List<InfraOdsTableMapping> findByConnectionIdOrderByCreatedDateDesc(UUID connectionId);

    Optional<InfraOdsTableMapping> findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(
        UUID connectionId,
        String streamName,
        String streamNamespace
    );

    Optional<InfraOdsTableMapping> findFirstByOdsSchemaIgnoreCaseAndOdsTableIgnoreCase(String odsSchema, String odsTable);

    default List<InfraOdsTableMapping> listAllSorted() {
        return findAll(Sort.by(Sort.Order.desc("createdDate")));
    }

    List<InfraOdsTableMapping> findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
}
