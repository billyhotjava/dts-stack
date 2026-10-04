package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelingSqlReadSetGuardTest {
    @Test void acceptsOnlyDeclaredPhysicalRelationsIncludingCteAndNestedQueries() {
        assertThatCode(() -> ModelingSqlReadSetGuard.requireDeclared("with a as (select id from ods.orders) select count(*) from a where id in (select id from ods.orders)", List.of("ods.orders"))).doesNotThrowAnyException();
    }
    @Test void acceptsStaticDbtReferencesAndInertConfiguration() {
        assertThatCode(() -> ModelingSqlReadSetGuard.requireDeclared("{{ config(materialized='table') }} select x.id from {{ ref('orders') }} x join {{ source('raw','items') }} y on x.id=y.id", List.of())).doesNotThrowAnyException();
    }
    @Test void detectsForeignRelationInsideNestedQueryOrUnion() {
        for (String sql : List.of("select id from ods.orders union all select id from secret.payroll", "select id from ods.orders where id in (select id from secret.payroll)", "with a as (select id from secret.payroll) select * from a"))
            assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared(sql, List.of("ods.orders"))).isInstanceOf(ModelSpecException.class);
    }
    @Test void rejectsDynamicReferencesWritesFunctionsAndUnqualifiedPhysicalAliases() {
        for (String sql : List.of("select * from {{ ref(var('table')) }}", "delete from ods.orders", "select * into copied from ods.orders", "select pg_read_file('/etc/passwd') from ods.orders", "select * from orders", "{{ config(post_hook='delete from x') }} select * from {{ ref('orders') }}", "select * from ods.orders;select * from ods.orders"))
            assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared(sql,List.of("ods.orders"))).isInstanceOf(ModelSpecException.class);
    }
    @Test void cannotGuessInternalPlaceholderOrBorrowCaseSensitiveNames() {
        assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared("select * from {{ ref('orders') }} join f9_declared_reference_0 x on 1=1",List.of())).isInstanceOf(ModelSpecException.class);
        assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared("select * from ods.\"ORDERS\"",List.of("ods.orders"))).isInstanceOf(ModelSpecException.class);
    }
    @Test void acceptsGeneratedLiteralMetadataAndColumnsButRejectsExecutableConfiguration() {
        assertThatCode(() -> ModelingSqlReadSetGuard.requireDeclared("{{ config(materialized='dts_schema_only', tags=['generated'], meta={'revision':1, 'modelSpecId':'abc'}, dts_columns=[{'name':'id','nullable':False,'data_type':'bigint'}], dts_primary_keys=['id']) }} select cast(null as bigint) as id", List.of())).doesNotThrowAnyException();
        for (String config : List.of("pre_hook='delete from secret'", "meta=env_var('SECRET')", "tags=['{{ run_query(1) }}']")) {
            assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared("{{ config(" + config + ") }} select 1", List.of())).isInstanceOf(ModelSpecException.class);
        }
    }
    @Test void acceptsCompilerDateDimensionAndDeduplicationWithoutHidingNestedSources() {
        assertThatCode(() -> ModelingSqlReadSetGuard.requireDeclared("select to_char(day_value,'YYYYMMDD')::integer, extract(year from day_value) from generate_series(date '2025-01-01',date '2025-12-31',interval '1 day') as calendar(day_value)",List.of())).doesNotThrowAnyException();
        assertThatCode(() -> ModelingSqlReadSetGuard.requireDeclared("select row_number() over(partition by id order by id) as n from ods.orders",List.of("ods.orders"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared("select row_number() over(order by (select id from secret.payroll)) from ods.orders",List.of("ods.orders"))).isInstanceOf(ModelSpecException.class);
        assertThatThrownBy(() -> ModelingSqlReadSetGuard.requireDeclared("select * from generate_series(1,(select id from secret.payroll))",List.of())).isInstanceOf(ModelSpecException.class);
    }
}
