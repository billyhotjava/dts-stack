package com.yuzhi.dts.copilot.analytics.service;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class FieldValuesService {

    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;

    public FieldValuesService(ExternalDatabaseDataSourceRegistry dataSourceRegistry) {
        this.dataSourceRegistry = dataSourceRegistry;
    }

    public List<String> distinctValues(long databaseId, String schema, String table, String column, int maxResults, @Nullable String search) throws SQLException {
        String qualifiedTable = qualifyTable(schema, table);
        String col = quoteIdentifier(column);

        boolean hasSearch = search != null && !search.isBlank();

        String sql = hasSearch
                ? "SELECT DISTINCT " + col + " AS v FROM " + qualifiedTable + " WHERE " + col + " IS NOT NULL AND LOWER(CAST(" + col + " AS VARCHAR)) LIKE LOWER(?)"
                : "SELECT DISTINCT " + col + " AS v FROM " + qualifiedTable + " WHERE " + col + " IS NOT NULL";

        HikariDataSource dataSource = dataSourceRegistry.get(databaseId);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (maxResults > 0) {
                statement.setMaxRows(maxResults);
            }
            if (hasSearch) {
                statement.setString(1, "%" + search.trim() + "%");
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) {
                    Object raw = rs.getObject(1);
                    if (raw == null) {
                        continue;
                    }
                    String v = raw.toString();
                    if (!v.isBlank()) {
                        out.add(v);
                    }
                }
                return out;
            }
        }
    }

    private static String qualifyTable(String schema, String table) {
        String t = quoteIdentifier(table);
        if (schema == null || schema.isBlank()) {
            return t;
        }
        return quoteIdentifier(schema) + "." + t;
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier cannot be null");
        }
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}

