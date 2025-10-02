package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSecureView;
import com.yuzhi.dts.platform.repository.catalog.CatalogSecureViewRepository;
import com.yuzhi.dts.platform.service.security.dto.SecurityViewExecutionResult;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityViewService {

    private final CatalogSecureViewRepository viewRepository;
    private final HiveStatementExecutor hiveStatementExecutor;

    public SecurityViewService(CatalogSecureViewRepository viewRepository, HiveStatementExecutor hiveStatementExecutor) {
        this.viewRepository = viewRepository;
        this.hiveStatementExecutor = hiveStatementExecutor;
    }

    /**
     * Build preview SQLs for a dataset according to policy under the "view route" (backup plan when no Ranger).
     * This does not execute DDL; it only returns statements for operators to apply on Hive/Trino side.
     */
    public Map<String, String> previewViews(CatalogDataset ds, CatalogAccessPolicy policy) {
        String table = ds.getHiveTable() != null && !ds.getHiveTable().isBlank() ? ds.getHiveTable() : ds.getName();
        String db = ds.getHiveDatabase();
        String trino = ds.getTrinoCatalog();
        String source = (trino != null && !trino.isBlank() ? (trino + ".") : "") +
            (db != null && !db.isBlank() ? (db + ".") : "") + table;
        Map<String, String> sql = new LinkedHashMap<>();

        // simple masking UDF placeholders
        String selectCols = "*"; // in real impl, expand and apply per-column masking based on tags

        sql.put("sv_" + table + "_public",
            "CREATE OR REPLACE VIEW sv_" + table + "_public AS\n" +
            "SELECT " + selectCols + " FROM " + source + " WHERE level IN ('PUBLIC')");
        sql.put("sv_" + table + "_internal",
            "CREATE OR REPLACE VIEW sv_" + table + "_internal AS\n" +
            "SELECT " + selectCols + " FROM " + source + " WHERE level IN ('PUBLIC','INTERNAL')");
        sql.put("sv_" + table + "_secret",
            "CREATE OR REPLACE VIEW sv_" + table + "_secret AS\n" +
            "SELECT " + selectCols + " FROM " + source + " WHERE level IN ('PUBLIC','INTERNAL','SECRET')");
        sql.put("sv_" + table + "_top_secret",
            "CREATE OR REPLACE VIEW sv_" + table + "_top_secret AS\n" +
            "SELECT " + selectCols + " FROM " + source);

        // Row filter (RLS) example if provided
        if (policy != null && policy.getRowFilter() != null && !policy.getRowFilter().isBlank()) {
            sql.replaceAll((k, v) -> v + " AND (" + policy.getRowFilter() + ")");
        }

        // GRANT example
        sql.put("grant_public", "GRANT SELECT ON TABLE sv_" + table + "_public TO ROLE_PUBLIC");
        sql.put("grant_internal", "GRANT SELECT ON TABLE sv_" + table + "_internal TO ROLE_INTERNAL");
        sql.put("grant_secret", "GRANT SELECT ON TABLE sv_" + table + "_secret TO ROLE_SECRET");
        sql.put("grant_top_secret", "GRANT SELECT ON TABLE sv_" + table + "_top_secret TO ROLE_TOP_SECRET");

        return sql;
    }

    @Transactional
    public SecurityViewExecutionResult applyViews(CatalogDataset dataset, CatalogAccessPolicy policy, String refreshOption) {
        Map<String, String> statements = previewViews(dataset, policy);
        String schemaHint = dataset.getHiveDatabase();
        List<StatementExecutionResult> executionResults = hiveStatementExecutor.execute(statements, schemaHint);
        boolean success = executionResults.stream().noneMatch(r -> r.status() == StatementExecutionResult.Status.FAILED);

        int persisted = 0;
        if (success) {
            List<CatalogSecureView> existing = viewRepository.findByDataset(dataset);
            if (!existing.isEmpty()) {
                viewRepository.deleteAll(existing);
            }
            List<CatalogSecureView> toSave = new ArrayList<>();
            for (Map.Entry<String, String> entry : statements.entrySet()) {
                if (!entry.getKey().startsWith("sv_")) {
                    continue; // skip grant helper rows
                }
                CatalogSecureView view = new CatalogSecureView();
                view.setDataset(dataset);
                view.setViewName(entry.getKey());
                view.setLevel(resolveLevel(entry.getKey()));
                view.setRefresh(refreshOption != null ? refreshOption : "NONE");
                view.setRowFilter(policy != null ? policy.getRowFilter() : null);
                toSave.add(view);
            }
            if (!toSave.isEmpty()) {
                viewRepository.saveAll(toSave);
                persisted = toSave.size();
            }
        }
        return new SecurityViewExecutionResult(statements, executionResults, persisted, success);
    }

    private String resolveLevel(String key) {
        if (key.endsWith("_public")) {
            return "PUBLIC";
        }
        if (key.endsWith("_internal")) {
            return "INTERNAL";
        }
        if (key.endsWith("_secret")) {
            return "SECRET";
        }
        return "TOP_SECRET";
    }
}
