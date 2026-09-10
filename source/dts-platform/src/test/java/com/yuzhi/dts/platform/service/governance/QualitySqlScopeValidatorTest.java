package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QualitySqlScopeValidatorTest {

    @Test
    void acceptsOnlyReadQueriesWhosePhysicalTablesMatchTheBoundAsset() {
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL")).isTrue();
        assertThat(allowed("SELECT l.id FROM public.ods_budget_v2 l JOIN public.ods_budget_v2 r ON r.id = l.id"))
            .isTrue();
        assertThat(
                allowed(
                    "WITH invalid_rows AS (SELECT id FROM public.ods_budget_v2) SELECT id FROM invalid_rows"
                )
            )
            .isTrue();
        assertThat(allowed("SELECT id FROM \"public\".\"ods_budget_v2\"")).isTrue();
        assertThat(
                allowed(
                    "SELECT id FROM public.ods_budget_v2 WHERE TRIM(CAST(project_no AS text)) = ''"
                )
            )
            .isTrue();
        assertThat(allowed("SELECT CAST(project_no AS numeric) FROM public.ods_budget_v2")).isTrue();
        assertThat(
                allowed(
                    "SELECT ROUND(COUNT(*) * 100.0 / NULLIF(COUNT(*), 0), 2) FROM public.ods_budget_v2"
                )
            )
            .isTrue();
    }

    @Test
    void rejectsCrossAssetAndCrossCatalogReferences() {
        assertThat(
                allowed(
                    "SELECT b.id, s.secret AS actual_value FROM public.ods_budget_v2 b, private.customer_secret s"
                )
            )
            .isFalse();
        assertThat(allowed("SELECT id FROM ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id FROM \"PUBLIC\".\"ODS_BUDGET_V2\"")).isFalse();
        assertThat(allowed("SELECT id FROM `public`.`ods_budget_v2`")).isFalse();
        assertThat(allowed("SELECT id FROM private.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id FROM another_catalog.public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id FROM ods_budget_v2@remote")).isFalse();
        assertThat(
                QualitySqlScopeValidator.referencesOnlyBoundTable(
                    "SELECT id FROM ods_budget_v2",
                    null,
                    "ods_budget_v2",
                    "POSTGRESQL"
                )
            )
            .isFalse();
        assertThat(
                QualitySqlScopeValidator.referencesOnlyBoundTable(
                    "SELECT id FROM `public`.`ods_budget_v2`",
                    "public",
                    "ods_budget_v2",
                    "INCEPTOR"
                )
            )
            .isTrue();
    }

    @Test
    void rejectsStatementsAndSourcesThatCannotBeProvenReadOnlyAndBound() {
        assertThat(allowed("SELECT id FROM public.ods_budget_v2; SELECT secret FROM private.customer_secret"))
            .isFalse();
        assertThat(allowed("SELECT id INTO leaked_rows FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 FOR UPDATE")).isFalse();
        assertThat(allowed("SELECT b.id FROM public.ods_budget_v2 b CROSS JOIN LATERAL dblink('remote', 'select 1') x"))
            .isFalse();
        assertThat(allowed("SELECT 1 AS id")).isFalse();
        assertThat(allowed("SELECT FROM")).isFalse();
        assertThat(
                allowedInceptor(
                    "SELECT * FROM public.ods_budget_v2 PIVOT (count((SELECT secret FROM private.customer_secret LIMIT 1)) FOR project_no IN ('x')) p"
                )
            )
            .isFalse();
        assertThat(
                allowedInceptor(
                    "SELECT * FROM public.ods_budget_v2 PIVOT (pg_read_file('/etc/passwd') FOR project_no IN ('x')) p"
                )
            )
            .isFalse();
        assertThat(
                allowedInceptor(
                    "SELECT * FROM public.ods_budget_v2 UNPIVOT (value FOR attribute IN (project_no)) u"
                )
            )
            .isFalse();
    }

    @Test
    void rejectsFunctionsOutsideTheQualityTemplateAllowlist() {
        assertThat(
                allowed(
                    "SELECT query_to_xml('SELECT secret FROM private.customer_secret', true, false, '') FROM public.ods_budget_v2"
                )
            )
            .isFalse();
        assertThat(allowed("SELECT pg_read_file('/etc/passwd') FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT lower(project_no) FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT pg_catalog.count(*) FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT nextval('private.sequence') FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT CAST(project_no AS custom_type) FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT CAST(project_no AS text[]) FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id, private.custom_type 'payload' FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id, private.custom_type $$payload$$ FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT id, private.custom_type E'payload' FROM public.ods_budget_v2")).isFalse();
        assertThat(allowed("SELECT project_no AS payload FROM public.ods_budget_v2")).isTrue();
        assertThat(allowed("SELECT project_no AS \"payload\" FROM public.ods_budget_v2")).isTrue();
        assertThat(
                allowed(
                    "SELECT count(id ORDER BY (SELECT secret FROM private.customer_secret LIMIT 1)) FROM public.ods_budget_v2"
                )
            )
            .isFalse();
    }

    @Test
    void keepsPostgresqlStringParsingAlignedWithTheReadOnlySession() {
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 WHERE project_no = 'plain\\value'")).isTrue();
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 WHERE project_no = E'escaped\\\\value'")).isTrue();
        assertThat(
                allowed(
                    "SELECT id FROM public.ods_budget_v2 WHERE project_no = 'safe\\' UNION SELECT secret FROM private.customer_secret"
                )
            )
            .isFalse();
        assertThat(
                allowed(
                    "SELECT id FROM public.ods_budget_v2 WHERE project_no = E'safe\\' UNION SELECT secret FROM private.customer_secret"
                )
            )
            .isFalse();
    }

    @Test
    void rejectsCrossAssetSubqueriesInEverySelectTailClause() {
        assertThat(
                allowed(
                    "SELECT id FROM public.ods_budget_v2 ORDER BY (SELECT secret FROM private.customer_secret LIMIT 1)"
                )
            )
            .isFalse();
        assertThat(
                allowed(
                    "SELECT count(*) FROM public.ods_budget_v2 GROUP BY (SELECT secret FROM private.customer_secret LIMIT 1)"
                )
            )
            .isFalse();
        assertThat(
                allowed(
                    "SELECT id FROM public.ods_budget_v2 OFFSET (SELECT secret::bigint FROM private.customer_secret LIMIT 1)"
                )
            )
            .isFalse();
        assertThat(
                allowed(
                    "SELECT DISTINCT ON ((SELECT secret FROM private.customer_secret LIMIT 1)) id FROM public.ods_budget_v2"
                )
            )
            .isFalse();
        assertThat(
                allowed(
                    "SELECT id FROM public.ods_budget_v2 LIMIT (SELECT secret::bigint FROM private.customer_secret LIMIT 1)"
                )
            )
            .isFalse();
    }

    @Test
    void acceptsTheTrimFamilyThatIsSemanticallyIdenticalToTheAllowedTrim() {
        // PostgreSQL TRIM(x) is btrim(x); rejecting only the explicit spelling was a trap, not a boundary.
        assertThat(allowed("SELECT * FROM public.ods_budget_v2 WHERE project_no IS NULL OR BTRIM(project_no) = ''"))
            .isTrue();
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 WHERE LTRIM(project_no) = ''")).isTrue();
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 WHERE RTRIM(project_no) = ''")).isTrue();
        assertThat(allowed("SELECT id FROM public.ods_budget_v2 WHERE btrim(CAST(project_no AS text)) = ''")).isTrue();
    }

    @Test
    void namesTheRejectedConstructSoOperatorsDoNotHaveToReadTheValidator() {
        QualitySqlScopeValidator.ScopeCheck unsupportedFunction = check(
            "SELECT lower(project_no) FROM public.ods_budget_v2"
        );
        assertThat(unsupportedFunction.allowed()).isFalse();
        assertThat(unsupportedFunction.reasonCode()).isEqualTo("UNSUPPORTED_FUNCTION");
        assertThat(unsupportedFunction.detail()).isEqualTo("lower");
        assertThat(unsupportedFunction.message()).contains("lower").contains("btrim");

        QualitySqlScopeValidator.ScopeCheck outOfScope = check(
            "SELECT b.id, s.secret FROM public.ods_budget_v2 b, private.customer_secret s"
        );
        assertThat(outOfScope.allowed()).isFalse();
        assertThat(outOfScope.reasonCode()).isEqualTo("OUT_OF_SCOPE_TABLE");
        assertThat(outOfScope.message()).contains("绑定资产以外的表");

        QualitySqlScopeValidator.ScopeCheck unsupportedCast = check(
            "SELECT CAST(project_no AS custom_type) FROM public.ods_budget_v2"
        );
        assertThat(unsupportedCast.allowed()).isFalse();
        assertThat(unsupportedCast.reasonCode()).isEqualTo("UNSUPPORTED_CAST_TYPE");
        assertThat(unsupportedCast.detail()).isEqualTo("custom_type");

        QualitySqlScopeValidator.ScopeCheck unsupportedSource = QualitySqlScopeValidator.checkScope(
            "SELECT id FROM public.ods_budget_v2",
            "public",
            "ods_budget_v2",
            "CLICKHOUSE"
        );
        assertThat(unsupportedSource.allowed()).isFalse();
        assertThat(unsupportedSource.reasonCode()).isEqualTo("UNSUPPORTED_SOURCE_TYPE");
        assertThat(unsupportedSource.detail()).isEqualTo("CLICKHOUSE");

        QualitySqlScopeValidator.ScopeCheck unparseable = check("SELECT FROM");
        assertThat(unparseable.allowed()).isFalse();
        assertThat(unparseable.reasonCode()).isEqualTo("UNPARSEABLE_SQL");

        QualitySqlScopeValidator.ScopeCheck pivot = QualitySqlScopeValidator.checkScope(
            "SELECT * FROM public.ods_budget_v2 UNPIVOT (value FOR attribute IN (project_no)) u",
            "public",
            "ods_budget_v2",
            "INCEPTOR"
        );
        assertThat(pivot.allowed()).isFalse();
        assertThat(pivot.reasonCode()).isEqualTo("UNSUPPORTED_SYNTAX");

        // An allowed function carrying a smuggled subquery must not be reported as "not in the allowlist".
        QualitySqlScopeValidator.ScopeCheck smuggledClause = check(
            "SELECT count(id ORDER BY (SELECT secret FROM private.customer_secret LIMIT 1)) FROM public.ods_budget_v2"
        );
        assertThat(smuggledClause.allowed()).isFalse();
        assertThat(smuggledClause.reasonCode()).isEqualTo("UNSUPPORTED_SYNTAX");
        assertThat(smuggledClause.message()).contains("count").doesNotContain("不在质量检测允许清单内");

        assertThat(check("SELECT id FROM public.ods_budget_v2").allowed()).isTrue();
        assertThat(check("SELECT id FROM public.ods_budget_v2").message()).isEmpty();
    }

    private static QualitySqlScopeValidator.ScopeCheck check(String sql) {
        return QualitySqlScopeValidator.checkScope(sql, "public", "ods_budget_v2", "POSTGRESQL");
    }

    private static boolean allowed(String sql) {
        return QualitySqlScopeValidator.referencesOnlyBoundTable(sql, "public", "ods_budget_v2", "POSTGRESQL");
    }

    private static boolean allowedInceptor(String sql) {
        return QualitySqlScopeValidator.referencesOnlyBoundTable(sql, "public", "ods_budget_v2", "INCEPTOR");
    }
}
