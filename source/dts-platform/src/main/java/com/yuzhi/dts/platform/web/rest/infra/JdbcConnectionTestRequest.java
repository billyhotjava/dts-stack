package com.yuzhi.dts.platform.web.rest.infra;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JdbcConnectionTestRequest {

    @NotBlank
    private String jdbcUrl;

    /** Optional explicit driver class name (e.g. dm.jdbc.driver.DmDriver). */
    private String driverClass;

    /** Optional username for JDBC authentication. */
    private String username;

    /** Optional password for JDBC authentication. */
    private String password;

    /** Additional JDBC properties (merged into the connection properties). */
    private Map<String, String> jdbcProperties;

    /** Optional validation query, defaults to `SELECT 1`. */
    private String testQuery;

    /** Optional notes carried to the backend (not used in connection test). */
    private String remarks;

    /** Optional dataSourceId for associating this test with an existing record. */
    private String dataSourceId;

    public JdbcConnectionTestRequest() {}

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = blankToNull(driverClass); }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = blankToNull(username); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = blankToNull(password); }

    public Map<String, String> getJdbcProperties() { return jdbcProperties == null ? Collections.emptyMap() : jdbcProperties; }
    public void setJdbcProperties(Map<String, String> jdbcProperties) { this.jdbcProperties = jdbcProperties; }

    public String getTestQuery() { return testQuery; }
    public void setTestQuery(String testQuery) { this.testQuery = blankToNull(testQuery); }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = blankToNull(remarks); }

    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String dataSourceId) { this.dataSourceId = blankToNull(dataSourceId); }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

