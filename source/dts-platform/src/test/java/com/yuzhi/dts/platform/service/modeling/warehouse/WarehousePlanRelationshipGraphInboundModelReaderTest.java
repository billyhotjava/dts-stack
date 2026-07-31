package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecDomainReadAccessPort;
import com.yuzhi.dts.platform.service.modeling.ModelSpecFeatureFlags;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.SqlArrayValue;

@ExtendWith(MockitoExtension.class)
class WarehousePlanRelationshipGraphInboundModelReaderTest {

    private static final String TENANT = "tenant-reader";
    private static final UUID PLAN_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID DOMAIN_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000001"
    );

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ModelSpecDomainReadAccessPort domainReadAccess;

    private WarehousePlanRelationshipGraphInboundModelReader reader;

    @BeforeEach
    void setUp() {
        reader = new WarehousePlanRelationshipGraphInboundModelReader(
            jdbcTemplate,
            domainReadAccess,
            new ModelSpecFeatureFlags(true, true)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesIndexedBoundTargetProbesWithoutExpandingTheHistoricalPrefix() {
        UUID previousWindowEnd = UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );
        ModelRevisionRef firstTarget = new ModelRevisionRef(
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            3
        );
        ModelRevisionRef secondTarget = new ModelRevisionRef(
            UUID.fromString("80000000-0000-0000-0000-000000000001"),
            5
        );
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(DOMAIN_ID));
        doReturn(List.of())
            .when(jdbcTemplate)
            .query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            );

        reader.listEarlierSources(
            TENANT,
            PLAN_ID,
            previousWindowEnd,
            List.of(firstTarget, secondTarget),
            501
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate)
            .query(
                sql.capture(),
                any(RowMapper.class),
                arguments.capture()
            );
        String normalizedSql = sql
            .getValue()
            .replaceAll("\\s+", " ")
            .trim();
        assertThat(normalizedSql)
            .contains(
                "s.domain_id = ANY (?::uuid[])",
                "s.depends_on @> cast(? as jsonb)",
                "s.dimension_refs @> cast(? as jsonb)",
                "s.id < ?",
                "order by s.id limit ?"
            )
            .doesNotContain(
                "jsonb_array_elements",
                "s.id <= ?",
                "coalesce(s.depends_on",
                "coalesce(s.dimension_refs",
                "s.domain_id in (",
                TENANT,
                firstTarget.modelSpecId().toString(),
                secondTarget.modelSpecId().toString()
            );
        assertThat(arguments.getValue())
            .doesNotContain(previousWindowEnd)
            .contains(
                firstTarget.modelSpecId(),
                secondTarget.modelSpecId(),
                """
                [{"modelSpecId":"70000000-0000-0000-0000-000000000001","revision":3}]\
                """,
                """
                [{"modelSpecId":"80000000-0000-0000-0000-000000000001","revision":5}]\
                """,
                501
            );
    }

    @Test
    void rejectsMoreThanFiveHundredTargetsBeforeQuerying() {
        List<ModelRevisionRef> targets = new ArrayList<>();
        for (int index = 0; index <= 500; index++) {
            targets.add(new ModelRevisionRef(new UUID(0, index + 1L), 1));
        }

        assertThatThrownBy(() ->
            reader.listEarlierSources(
                TENANT,
                PLAN_ID,
                new UUID(0, 1),
                targets,
                501
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Relationship graph inbound targets exceed 500");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersIndexedMatchesAtThePreviousWindowBoundaryInPostgresUuidOrder() {
        UUID previousWindowEnd = UUID.fromString(
            "7fffffff-ffff-ffff-ffff-ffffffffffff"
        );
        ModelRevisionRef earlierSource = new ModelRevisionRef(
            UUID.fromString("7fffffff-ffff-ffff-ffff-fffffffffffe"),
            1
        );
        ModelRevisionRef currentWindowSource = new ModelRevisionRef(
            UUID.fromString("80000000-0000-0000-0000-000000000000"),
            1
        );
        ModelRevisionRef target = new ModelRevisionRef(
            UUID.fromString("80000000-0000-0000-0000-000000000001"),
            1
        );
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of());
        doReturn(List.of(earlierSource, currentWindowSource))
            .when(jdbcTemplate)
            .query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            );

        assertThat(
            reader.listEarlierSources(
                TENANT,
                PLAN_ID,
                previousWindowEnd,
                List.of(target),
                501
            )
        )
            .containsExactly(earlierSource);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bindsThousandsOfVisibleDomainsAsOnePostgresUuidArray()
        throws Exception {
        Set<UUID> visibleDomains = new LinkedHashSet<>();
        for (int index = 0; index < 2_000; index++) {
            visibleDomains.add(new UUID(index, index + 1L));
        }
        ModelRevisionRef target = new ModelRevisionRef(
            UUID.fromString("90000000-0000-0000-0000-000000000001"),
            1
        );
        when(domainReadAccess.visibleDomainIds())
            .thenReturn(visibleDomains);
        doReturn(List.of())
            .when(jdbcTemplate)
            .query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            );

        reader.listCurrentTargets(
            TENANT,
            PLAN_ID,
            List.of(target)
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate)
            .query(
                sql.capture(),
                any(RowMapper.class),
                arguments.capture()
            );
        assertThat(sql.getValue())
            .contains("s.domain_id = ANY (?::uuid[])")
            .doesNotContain("s.domain_id in (");
        assertThat(arguments.getValue()).hasSize(7);
        assertThat(arguments.getValue()[2])
            .isInstanceOf(SqlArrayValue.class);

        PreparedStatement statement = mock(PreparedStatement.class);
        Connection connection = mock(Connection.class);
        Array sqlArray = mock(Array.class);
        when(statement.getConnection()).thenReturn(connection);
        when(
            connection.createArrayOf(
                eq("uuid"),
                any(Object[].class)
            )
        )
            .thenReturn(sqlArray);
        SqlArrayValue domainArray =
            (SqlArrayValue) arguments.getValue()[2];
        domainArray.setValue(statement, 3);

        ArgumentCaptor<Object[]> elements = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(connection)
            .createArrayOf(eq("uuid"), elements.capture());
        assertThat(elements.getValue())
            .hasSize(2_000)
            .containsExactlyElementsOf(visibleDomains);
        verify(statement).setArray(3, sqlArray);
        domainArray.cleanup();
        verify(sqlArray).free();
    }
}
