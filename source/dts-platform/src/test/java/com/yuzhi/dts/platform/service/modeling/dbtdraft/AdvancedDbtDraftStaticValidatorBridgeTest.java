package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdvancedDbtDraftStaticValidatorBridgeTest {

    private final AdvancedDbtDraftStaticValidator validator = new AdvancedDbtDraftStaticValidator();

    @Test
    void validatesAnIsolatedDraftThroughTheExistingSourceProjectAdapterWithoutExecution() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", "name: sprint83\nversion: 1.0\nmodel-paths: [models]\n");
        files.put("models/orders.sql", "{{ config(materialized='table') }}\nselect * from {{ source('raw', 'orders') }}\n");

        AdvancedDbtDraftStaticValidator.ValidatedProject result = validator.validate(files);

        assertThat(result.projectKey()).isEqualTo("sprint83");
        assertThat(result.validatedChecksum()).matches("^[0-9a-f]{64}$");
        assertThat(result.nodes()).extracting(AdvancedDbtDraftStaticValidator.ValidatedNode::dbtUniqueId)
            .containsExactly("model.sprint83.orders");
        assertThat(result.nodes().getFirst().sql()).contains("source('raw', 'orders')");
        assertThat(result.diagnostics()).extracting(AdvancedDbtDraftStaticValidator.StaticDiagnostic::code)
            .contains("DBT_SOURCE_PROJECT_STATIC_ANALYSIS");
    }

    @Test
    void acceptsLiteralReferencesForResolutionByTheCanonicalCandidateBuilder() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", "name: sprint83\nversion: 1.0\nmodel-paths: [models]\n");
        files.put("models/orders.sql", "select * from {{ ref('shared_orders') }}\n");

        AdvancedDbtDraftStaticValidator.ValidatedProject result = validator.validate(files);

        assertThat(result.nodes()).extracting(AdvancedDbtDraftStaticValidator.ValidatedNode::dbtUniqueId)
            .containsExactly("model.sprint83.orders");
        assertThat(result.diagnostics()).extracting(AdvancedDbtDraftStaticValidator.StaticDiagnostic::code)
            .contains("DBT_SOURCE_PROJECT_REF_UNRESOLVED");
    }

    @Test
    void preservesExplicitFieldRolesFromAnEnforcedSourceSchema() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", "name: sprint83\nversion: 1.0\nmodel-paths: [models]\n");
        files.put("models/orders.sql", "{{ config(materialized='table') }}\nselect 1 as project_total_cnt\n");
        files.put(
            "models/schema.yml",
            """
            version: 2
            models:
              - name: orders
                config:
                  contract:
                    enforced: true
                meta:
                  dts:
                    fieldRoles:
                      project_total_cnt: MEASURE
                columns:
                  - name: project_total_cnt
                    description: 项目总数
                    data_type: bigint
            """
        );

        AdvancedDbtDraftStaticValidator.ValidatedProject result = validator.validate(files);

        assertThat(result.nodes().getFirst().schema())
            .contains("\"name\":\"project_total_cnt\"")
            .contains("\"role\":\"MEASURE\"");
    }

    @Test
    void acceptsTheLiteralOnlyConfigShapeEmittedByTheCanonicalModelingCompiler() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", "name: dts\nversion: 1.0\nmodel-paths: [models]\n");
        files.put(
            "models/orders.sql",
            "{{ config(materialized='incremental', alias='dwd_orders', unique_key=['order_id'], " +
            "meta={'tenantId':'tenant-a','modelSpecId':'model-a','revision':2,'retentionDays':365}) }}\n" +
            "select 1 as order_id\n"
        );

        AdvancedDbtDraftStaticValidator.ValidatedProject result = validator.validate(files);

        assertThat(result.projectKey()).isEqualTo("dts");
        assertThat(result.nodes()).extracting(AdvancedDbtDraftStaticValidator.ValidatedNode::dbtUniqueId)
            .containsExactly("model.dts.orders");
    }

    @Test
    void rejectsDynamicReferencesAsUnsupportedInsteadOfRunningDbt() {
        Map<String, String> files = Map.of(
            "dbt_project.yml",
            "name: sprint83\n",
            "models/orders.sql",
            "select * from {{ ref(var('dynamic_model')) }}\n"
        );

        assertThatThrownBy(() -> validator.validate(files))
            .isInstanceOf(AdvancedDbtDraftStaticValidator.StaticValidationException.class)
            .hasMessageContaining("static");
    }

    @ParameterizedTest
    @MethodSource("unsafeStaticOnlyConstructs")
    void rejectsSideEffectingOrImplicitContextJinjaWithoutExecutingIt(String path, String content) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n");
        files.put("models/orders.sql", "select 1\n");
        files.put(path, content);

        assertThatThrownBy(() -> validator.validate(files))
            .isInstanceOf(AdvancedDbtDraftStaticValidator.StaticValidationException.class)
            .extracting(error -> ((AdvancedDbtDraftStaticValidator.StaticValidationException) error).code())
            .isEqualTo("DBT_DRAFT_JINJA_UNSUPPORTED");
    }

    private static Stream<Arguments> unsafeStaticOnlyConstructs() {
        return Stream.of(
            Arguments.of("models/orders.sql", "select {{ run_query('delete from orders') }}"),
            Arguments.of("models/orders.sql", "{% call statement('unsafe', fetch_result=true) %} select 1 {% endcall %}"),
            Arguments.of("models/orders.sql", "select '{{ env_var('DB_PASSWORD') }}'"),
            Arguments.of("models/orders.sql", "select '{{ modules.datetime.datetime.now() }}'"),
            Arguments.of("models/orders.sql", "select '{{ adapter.dispatch('macro_name')() }}'"),
            Arguments.of("models/orders.sql", "select '{{ var('runtime_relation') }}'"),
            Arguments.of(
                "models/orders.sql",
                "{{ config(meta={'secret': env_var('DB_PASSWORD')}) }}\nselect 1"
            ),
            Arguments.of(
                "models/orders.sql",
                "{{ config(meta={'nested': {'value':'unsafe'}}) }}\nselect 1"
            ),
            Arguments.of("models/orders.sql", "{{ config({pre_hook: 'delete from audit_log'}) }}\nselect 1"),
            Arguments.of("models/orders.sql", "select {{ context['run_query']('delete from audit_log') }}"),
            Arguments.of("models/orders.sql", "select {{ adapter['dispatch']('unsafe')() }}"),
            Arguments.of("models/orders.sql", "{% set query = run_query %}{{ query('delete from audit_log') }}"),
            Arguments.of("models/orders.sql", "{% macro unsafe() %}delete from audit_log{% endmacro %}select 1"),
            Arguments.of("dbt_project.yml", "name: sprint83\n\"on-run-start\": ['delete from audit_log']\n"),
            Arguments.of(
                "dbt_project.yml",
                "name: sprint83\nmodels:\n  sprint83:\n    +pre-hook: ['delete from audit_log']\n"
            )
        );
    }
}
