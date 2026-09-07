package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceOnlyJinjaGuardSchemaTest {
    @Test
    void acceptsOnlyLiteralDeclaredSchemaConfiguration() {
        String columns = "[{'name':'record_id','data_type':'bigint','nullable':False}]";
        String sql = "{{ config(materialized='dts_schema_only', dts_columns=" + columns + ", dts_primary_keys=['record_id']) }}";
        assertThat(SourceOnlyJinjaGuard.inspect(sql).dynamic()).isFalse();
        assertThat(SourceOnlyJinjaGuard.inspect(sql).materialization()).isEqualTo("dts_schema_only");
        for (String invalid : new String[] {
            sql.replace("False", "env_var('NULLABLE')"),
            sql.replace("bigint", "bigint);drop table orders;--"),
            sql.replace("'nullable':False", "'nullable':False,'extra':'value'"),
            sql.replace("'record_id'", "'bad-name'"),
            sql.replace("dts_schema_only", "table"),
            "{{ config(materialized='dts_schema_only') }}"
        }) {
            assertThat(SourceOnlyJinjaGuard.inspect(invalid).dynamic()).as(invalid).isTrue();
        }
    }
}
