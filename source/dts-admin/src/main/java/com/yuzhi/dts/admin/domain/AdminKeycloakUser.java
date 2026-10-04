package com.yuzhi.dts.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_keycloak_user")
public class AdminKeycloakUser extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "kc_id", nullable = false, unique = true, length = 64)
    private String keycloakId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "full_name", length = 128)
    private String fullName;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "person_security_level", nullable = false, length = 32)
    private String personSecurityLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "realm_roles", columnDefinition = "jsonb")
    private List<String> realmRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "group_paths", columnDefinition = "jsonb")
    private List<String> groupPaths = new ArrayList<>();

    /**
     * MDM 推送的原始人员编码（保留大小写）。
     *
     * <p>Keycloak 用户名一律转小写，只差大小写的两个编码在 Keycloak 中无法共存；
     * 按「不同编码即不同账号」的约定，导入时用这一列识别冲突。
     */
    @Column(name = "person_code", length = 64)
    private String personCode;

    /** Keycloak user attribute {@code dept_code} 的本地镜像；部门归属的唯一读取来源。 */
    @Column(name = "dept_code", length = 64)
    private String deptCode;

    /** Keycloak user attribute {@code dept_name} 的本地镜像。 */
    @Column(name = "dept_name", length = 128)
    private String deptName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "mdm_enabled", nullable = false)
    private int mdmEnabled = 1;

    /**
     * F11-T07：人员/组织字段的权威来源（MDM/LOCAL），与 Keycloak 回读的认证字段分离。
     * 同一记录禁止 MDM 与本地互相覆盖，由 PersonnelSourceFieldGuard 在应用层执行。
     */
    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    /** F11-T07：来源记录版本，用于 CAS 防乱序；无版本时不判先后，只做幂等。 */
    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    /**
     * F11-T07：DTS 业务准入状态（ACTIVE/SUSPENDED/RETIRED），独立于 Keycloak enabled。
     * 人工停用不被 MDM 活跃值解除；未知来源状态不猜测离职。
     */
    @Column(name = "access_state", nullable = false, length = 16)
    private String accessState = "ACTIVE";

    /**
     * F11-T07：对外同步结果（PENDING/APPLYING/SYNCED/RETRYABLE_FAILURE/CONFLICT），
     * 只表示开户/投影进度，不代表有权登录。
     */
    @Column(name = "sync_state", nullable = false, length = 32)
    private String syncState = "SYNCED";

    /** F11-T07：最近一次同步失败原因（脱敏，不存密钥/令牌）。 */
    @Column(name = "sync_error", columnDefinition = "TEXT")
    private String syncError;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPersonSecurityLevel() {
        return personSecurityLevel;
    }

    public void setPersonSecurityLevel(String personSecurityLevel) {
        this.personSecurityLevel = personSecurityLevel;
    }

    public List<String> getRealmRoles() {
        return realmRoles;
    }

    public void setRealmRoles(List<String> realmRoles) {
        this.realmRoles = realmRoles == null ? new ArrayList<>() : realmRoles;
    }

    public List<String> getGroupPaths() {
        return groupPaths;
    }

    public void setGroupPaths(List<String> groupPaths) {
        this.groupPaths = groupPaths == null ? new ArrayList<>() : groupPaths;
    }

    public String getPersonCode() {
        return personCode;
    }

    public void setPersonCode(String personCode) {
        this.personCode = personCode;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMdmEnabled() {
        return mdmEnabled;
    }

    public void setMdmEnabled(int mdmEnabled) {
        this.mdmEnabled = mdmEnabled;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getAccessState() {
        return accessState;
    }

    public void setAccessState(String accessState) {
        this.accessState = accessState;
    }

    public String getSyncState() {
        return syncState;
    }

    public void setSyncState(String syncState) {
        this.syncState = syncState;
    }

    public String getSyncError() {
        return syncError;
    }

    public void setSyncError(String syncError) {
        this.syncError = syncError;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
