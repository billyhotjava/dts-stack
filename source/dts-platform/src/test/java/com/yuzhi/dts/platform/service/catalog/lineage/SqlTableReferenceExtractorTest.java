package com.yuzhi.dts.platform.service.catalog.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SqlTableReferenceExtractorTest {

    private final SqlTableReferenceExtractor extractor = new SqlTableReferenceExtractor();

    @Test
    void extractFindsFromJoinUpdateInto() {
        String sql = """
            SELECT *
            FROM ods.t_user u
            JOIN dwd.t_order o ON o.user_id = u.id
            """;

        Set<SqlTableReferenceExtractor.TableRef> refs = extractor.extract(sql);

        assertThat(refs)
            .extracting(SqlTableReferenceExtractor.TableRef::normalizedSchema, SqlTableReferenceExtractor.TableRef::normalizedTable)
            .containsExactlyInAnyOrder(
                tuple("ods", "t_user"),
                tuple("dwd", "t_order")
            );
    }

    @Test
    void extractHandlesQuotedIdentifiers() {
        String sql = """
            SELECT *
            FROM "ODS"."T_USER"
            JOIN [DWD].[T_ORDER] o ON 1=1
            JOIN `ADS`.`T_KPI` k ON 1=1
            """;

        Set<SqlTableReferenceExtractor.TableRef> refs = extractor.extract(sql);

        assertThat(refs)
            .extracting(SqlTableReferenceExtractor.TableRef::normalizedSchema, SqlTableReferenceExtractor.TableRef::normalizedTable)
            .containsExactlyInAnyOrder(
                tuple("ods", "t_user"),
                tuple("dwd", "t_order"),
                tuple("ads", "t_kpi")
            );
    }

    @Test
    void extractStripsStringsAndComments() {
        String sql = """
            -- FROM fake.table should be ignored
            SELECT 'join ods.x' AS text
            FROM ods.real_table
            /* join dwd.y should be ignored */
            JOIN dwd.real_order o ON 1=1
            """;

        Set<SqlTableReferenceExtractor.TableRef> refs = extractor.extract(sql);

        assertThat(refs)
            .extracting(SqlTableReferenceExtractor.TableRef::normalizedSchema, SqlTableReferenceExtractor.TableRef::normalizedTable)
            .containsExactlyInAnyOrder(
                tuple("ods", "real_table"),
                tuple("dwd", "real_order")
            );
    }

    @Test
    void extractSkipsCteNames() {
        String sql = """
            WITH tmp AS (
              SELECT * FROM ods.base_table
            )
            SELECT * FROM tmp
            JOIN dwd.other_table t ON 1=1
            """;

        Set<SqlTableReferenceExtractor.TableRef> refs = extractor.extract(sql);

        assertThat(refs)
            .extracting(SqlTableReferenceExtractor.TableRef::normalizedSchema, SqlTableReferenceExtractor.TableRef::normalizedTable)
            .containsExactlyInAnyOrder(
                tuple("ods", "base_table"),
                tuple("dwd", "other_table")
            );
    }

    @Test
    void extractSkipsJoinLateralKeyword() {
        String sql = """
            SELECT *
            FROM ods.a
            JOIN LATERAL (SELECT 1) t ON 1=1
            """;

        Set<SqlTableReferenceExtractor.TableRef> refs = extractor.extract(sql);

        assertThat(refs)
            .extracting(SqlTableReferenceExtractor.TableRef::normalizedSchema, SqlTableReferenceExtractor.TableRef::normalizedTable)
            .containsExactly(tuple("ods", "a"));
    }
}

