package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidenceRequest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class F8QualityEvidencePostgresTest {
    @Test void draftRunsCannotReplaceFormalEvidenceAndNewViolationCannotPass() {
        try (var database = new PostgreSQLContainer<>("postgres:17.6")) {
            database.start();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            jdbc.execute("create table catalog_asset_semantic_projection(asset_type text, asset_key text, resource_id uuid)");
            jdbc.execute("create table gov_rule_version(id uuid, rule_id uuid, status text)");
            jdbc.execute("create table gov_rule_binding(id uuid, dataset_id uuid, rule_version_id uuid)");
            jdbc.execute("create table gov_quality_run(id uuid, rule_id uuid, rule_version_id uuid, binding_id uuid, dataset_id uuid, status text, trigger_type text, metrics_json text, finished_at timestamp, started_at timestamp, scheduled_at timestamp, created_date timestamp)");
            UUID dataset=UUID.randomUUID(), rule=UUID.randomUUID(), version=UUID.randomUUID(), binding=UUID.randomUUID(), formal=UUID.randomUUID(), draft=UUID.randomUUID();
            String key="source:test/schema:public/table:projects";
            jdbc.update("insert into catalog_asset_semantic_projection values ('DATASET',?,?)", key,dataset);
            jdbc.update("insert into gov_rule_version values (?,?,'PUBLISHED')",version,rule);
            jdbc.update("insert into gov_rule_binding values (?,?,?)",binding,dataset,version);
            String insert="insert into gov_quality_run values (?,?,?,?,?,?,?, ?, now() - interval '5 seconds',now() - interval '10 seconds',now() - interval '10 seconds',now() - interval '10 seconds')";
            jdbc.update(insert, formal,rule,version,binding,dataset,"SUCCEEDED","MANUAL",null);
            jdbc.update(insert, draft,rule,version,binding,dataset,"FAILED","DRY_RUN",null);
            jdbc.update("update gov_quality_run set finished_at=now() - interval '1 second' where id=?",draft);
            var adapter=new JdbcGovernanceQualityEvidenceAdapter(jdbc);
            var request=List.of(new QualityEvidenceRequest(CatalogAssetType.DATASET,key,List.of(version),Instant.now(),300));
            var passing=adapter.read(request).getFirst();
            assertThat(passing.runId()).isEqualTo(formal); assertThat(passing.passed()).isTrue();
            jdbc.update("update gov_quality_run set metrics_json=? where id=?",new QualityExecutionOutcome(1,"VIOLATION","OK","EXACT",1L,List.of()).json(),formal);
            assertThat(adapter.read(request).getFirst().passed()).isFalse();
            jdbc.update("delete from gov_quality_run where id=?",formal);
            assertThat(adapter.read(request).getFirst().violations()).contains("MISSING");
        }
    }
}
