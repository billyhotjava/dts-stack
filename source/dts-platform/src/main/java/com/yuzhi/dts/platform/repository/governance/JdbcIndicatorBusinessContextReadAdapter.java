package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextReadPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JdbcIndicatorBusinessContextReadAdapter implements IndicatorBusinessContextReadPort {

    private final JdbcTemplate jdbc;

    public JdbcIndicatorBusinessContextReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DomainNode> domain(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                jdbc.queryForObject(
                    "SELECT id, parent_id, lifecycle_status FROM catalog_domain WHERE id = ?",
                    (rs, rowNum) -> new DomainNode(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        rs.getString("lifecycle_status")
                    ),
                    id
                )
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<BusinessProcessNode> businessProcess(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                jdbc.queryForObject(
                    "SELECT id, domain_id, confirmed FROM sprint64_business_process WHERE id = ?",
                    (rs, rowNum) -> new BusinessProcessNode(
                        rs.getObject("id", UUID.class),
                        rs.getObject("domain_id", UUID.class),
                        rs.getBoolean("confirmed")
                    ),
                    id
                )
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }
}
