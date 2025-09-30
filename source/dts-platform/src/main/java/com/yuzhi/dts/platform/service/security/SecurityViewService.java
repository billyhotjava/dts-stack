package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SecurityViewService {

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
}
