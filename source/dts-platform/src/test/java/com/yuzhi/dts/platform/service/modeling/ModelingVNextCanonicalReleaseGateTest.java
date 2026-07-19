package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ModelingVNextCanonicalReleaseGateTest {

    @Test
    void canonicalReleaseRegistrationUsesConfirmedPlanDomainAndNeverRequiresABusinessObject() {
        ModelSpecView canonicalView = canonicalView();
        RegistrationJdbcTemplate confirmed = new RegistrationJdbcTemplate(true);
        ModelingVNextApplicationService confirmedService = service(confirmed, canonicalView);

        ModelingVNextApplicationService.ReleaseGateView registered = confirmedService.releaseGate(
            "server-tenant",
            canonicalView.id().toString()
        );

        assertThat(registered.blockers()).doesNotContain("UNREGISTERED_BUSINESS_OBJECT");
        assertThat(confirmed.businessObjectQueried).isFalse();

        RegistrationJdbcTemplate unconfirmed = new RegistrationJdbcTemplate(false);
        ModelingVNextApplicationService unconfirmedService = service(unconfirmed, canonicalView);

        ModelingVNextApplicationService.ReleaseGateView blocked = unconfirmedService.releaseGate(
            "server-tenant",
            canonicalView.id().toString()
        );

        assertThat(blocked.blockers()).contains("UNREGISTERED_BUSINESS_OBJECT");
        assertThat(unconfirmed.businessObjectQueried).isFalse();
    }

    private static ModelingVNextApplicationService service(JdbcTemplate jdbcTemplate, ModelSpecView view) {
        ModelSpecApplicationService canonical = mock(ModelSpecApplicationService.class);
        when(canonical.canonicalReadEnabled()).thenReturn(true);
        when(canonical.get("server-tenant", view.id())).thenReturn(view);
        return new ModelingVNextApplicationService(
            jdbcTemplate,
            new ObjectMapper().findAndRegisterModules(),
            null,
            canonical
        );
    }

    private static ModelSpecView canonicalView() {
        return new ModelSpecView(
            2,
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            ModelType.FACT,
            Layer.DWD,
            "customer_detail",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.customer",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "v1"
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static final class RegistrationJdbcTemplate extends JdbcTemplate {

        private final boolean registered;
        private boolean businessObjectQueried;

        private RegistrationJdbcTemplate(boolean registered) {
            this.registered = registered;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("modeling_business_object")) businessObjectQueried = true;
            throw new EmptyResultDataAccessException(1);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("modeling_business_object")) businessObjectQueried = true;
            if (requiredType == Boolean.class && sql.stripLeading().startsWith("select contract_version = 2")) {
                return requiredType.cast(Boolean.TRUE);
            }
            if (requiredType == Boolean.class && sql.contains("select exists")) return requiredType.cast(registered);
            if (requiredType == Integer.class && sql.contains("select count(*)")) return requiredType.cast(1);
            throw new EmptyResultDataAccessException(1);
        }
    }
}
