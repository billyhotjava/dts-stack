package com.yuzhi.dts.platform.web.rest.infra;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HiveConnectionTestRequest {

    public enum AuthMethod { KEYTAB, PASSWORD }

    @NotBlank
    private String jdbcUrl;

    /** Kerberos identity in the form user@REALM used to acquire TGT */
    @NotBlank
    private String loginPrincipal;

    /** Optional short username used for JDBC property `user` or proxy. */
    private String loginUser;

    /** Kerberos realm, required when krb5Conf is not provided. */
    private String realm;

    /** List of KDC host[:port] declarations. */
    private List<String> kdcs;

    /** Full krb5.conf content (optional, overrides realm/kdc). */
    private String krb5Conf;

    @NotNull
    private AuthMethod authMethod = AuthMethod.KEYTAB;

    /** Base64 encoded keytab content when authMethod = KEYTAB. */
    private String keytabBase64;

    /** Optional file name for keytab, used when saving to disk during test. */
    private String keytabFileName;

    /** Kerberos password when authMethod = PASSWORD. */
    private String password;

    /** Additional JDBC session properties. */
    private Map<String, String> jdbcProperties;

    /** Optional proxy user (impersonation). */
    private String proxyUser;

    /** Optional validation query, defaults to `SELECT 1`. */
    private String testQuery;

    /** Optional notes carried to the backend (not used in connection test). */
    private String remarks;

    public HiveConnectionTestRequest() {}

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getLoginPrincipal() { return loginPrincipal; }
    public void setLoginPrincipal(String loginPrincipal) { this.loginPrincipal = loginPrincipal; }

    public String getLoginUser() { return loginUser; }
    public void setLoginUser(String loginUser) { this.loginUser = blankToNull(loginUser); }

    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = blankToNull(realm); }

    public List<String> getKdcs() {
        if (kdcs == null) {
            return Collections.emptyList();
        }
        return kdcs;
    }

    public void setKdcs(List<String> kdcs) {
        if (kdcs == null) {
            this.kdcs = null;
        } else {
            var trimmed = new ArrayList<String>(kdcs.size());
            for (String item : kdcs) {
                if (item != null && !item.isBlank()) {
                    trimmed.add(item.trim());
                }
            }
            this.kdcs = trimmed.isEmpty() ? null : trimmed;
        }
    }

    public String getKrb5Conf() { return krb5Conf; }
    public void setKrb5Conf(String krb5Conf) { this.krb5Conf = blankToNull(krb5Conf); }

    public AuthMethod getAuthMethod() { return authMethod; }
    public void setAuthMethod(AuthMethod authMethod) { this.authMethod = authMethod; }

    public String getKeytabBase64() { return keytabBase64; }
    public void setKeytabBase64(String keytabBase64) { this.keytabBase64 = blankToNull(keytabBase64); }

    public String getKeytabFileName() { return keytabFileName; }
    public void setKeytabFileName(String keytabFileName) { this.keytabFileName = blankToNull(keytabFileName); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = blankToNull(password); }

    public Map<String, String> getJdbcProperties() { return jdbcProperties == null ? Collections.emptyMap() : jdbcProperties; }
    public void setJdbcProperties(Map<String, String> jdbcProperties) { this.jdbcProperties = jdbcProperties; }

    public String getProxyUser() { return proxyUser; }
    public void setProxyUser(String proxyUser) { this.proxyUser = blankToNull(proxyUser); }

    public String getTestQuery() { return testQuery; }
    public void setTestQuery(String testQuery) { this.testQuery = blankToNull(testQuery); }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = blankToNull(remarks); }

    @JsonIgnore
    @AssertTrue(message = "必须提供 krb5.conf 或 realm/kdc 信息")
    public boolean isKrb5Available() {
        return (krb5Conf != null && !krb5Conf.isBlank()) || (realm != null && !realm.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "Keytab 认证模式需要上传 Keytab 文件")
    public boolean isKeytabProvided() {
        if (authMethod == AuthMethod.KEYTAB) {
            return keytabBase64 != null && !keytabBase64.isBlank();
        }
        return true;
    }

    @JsonIgnore
    @AssertTrue(message = "密码认证模式需要填写密码")
    public boolean isPasswordProvided() {
        if (authMethod == AuthMethod.PASSWORD) {
            return password != null && !password.isBlank();
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "HiveConnectionTestRequest{" +
            "jdbcUrl='" + jdbcUrl + '\'' +
            ", loginPrincipal='" + loginPrincipal + '\'' +
            ", loginUser='" + loginUser + '\'' +
            ", realm='" + realm + '\'' +
            ", kdcs=" + getKdcs() +
            ", authMethod=" + authMethod +
            ", proxyUser='" + proxyUser + '\'' +
            ", testQuery='" + testQuery + '\'' +
            '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(jdbcUrl, loginPrincipal, loginUser, realm, getKdcs(), authMethod, proxyUser, testQuery);
    }
}

