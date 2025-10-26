package com.yuzhi.dts.platform.domain.security;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "portal_sessions")
public class PortalSessionEntity implements Serializable {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "normalized_username", nullable = false, length = 255)
    private String normalizedUsername;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "access_token", nullable = false, length = 128, unique = true)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 128, unique = true)
    private String refreshToken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles", nullable = false, columnDefinition = "jsonb")
    private List<String> roles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", nullable = false, columnDefinition = "jsonb")
    private List<String> permissions = new ArrayList<>();

    @Column(name = "dept_code", length = 128)
    private String deptCode;

    @Column(name = "personnel_level", length = 64)
    private String personnelLevel;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private PortalSessionCloseReason revokedReason;

    @Column(name = "revoked_by_session_id", columnDefinition = "uuid")
    private UUID revokedBySessionId;

    @Column(name = "admin_access_token")
    private String adminAccessToken;

    @Column(name = "admin_access_token_expires_at")
    private Instant adminAccessTokenExpiresAt;

    @Column(name = "admin_refresh_token")
    private String adminRefreshToken;

    @Column(name = "admin_refresh_token_expires_at")
    private Instant adminRefreshTokenExpiresAt;

    public PortalSessionEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public void setNormalizedUsername(String normalizedUsername) {
        this.normalizedUsername = normalizedUsername;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getPersonnelLevel() {
        return personnelLevel;
    }

    public void setPersonnelLevel(String personnelLevel) {
        this.personnelLevel = personnelLevel;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public PortalSessionCloseReason getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(PortalSessionCloseReason revokedReason) {
        this.revokedReason = revokedReason;
    }

    public UUID getRevokedBySessionId() {
        return revokedBySessionId;
    }

    public void setRevokedBySessionId(UUID revokedBySessionId) {
        this.revokedBySessionId = revokedBySessionId;
    }

    public String getAdminAccessToken() {
        return adminAccessToken;
    }

    public void setAdminAccessToken(String adminAccessToken) {
        this.adminAccessToken = adminAccessToken;
    }

    public Instant getAdminAccessTokenExpiresAt() {
        return adminAccessTokenExpiresAt;
    }

    public void setAdminAccessTokenExpiresAt(Instant adminAccessTokenExpiresAt) {
        this.adminAccessTokenExpiresAt = adminAccessTokenExpiresAt;
    }

    public String getAdminRefreshToken() {
        return adminRefreshToken;
    }

    public void setAdminRefreshToken(String adminRefreshToken) {
        this.adminRefreshToken = adminRefreshToken;
    }

    public Instant getAdminRefreshTokenExpiresAt() {
        return adminRefreshTokenExpiresAt;
    }

    public void setAdminRefreshTokenExpiresAt(Instant adminRefreshTokenExpiresAt) {
        this.adminRefreshTokenExpiresAt = adminRefreshTokenExpiresAt;
    }
}
