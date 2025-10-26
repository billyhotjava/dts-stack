package com.yuzhi.dts.admin.security.session;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_sessions")
public class AdminSessionEntity implements Serializable {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "normalized_username", nullable = false, length = 255)
    private String normalizedUsername;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "session_state", length = 255)
    private String sessionState;

    @Column(name = "access_token_hash", nullable = false, length = 96, unique = true)
    private String accessTokenHash;

    @Column(name = "refresh_token_hash", length = 96, unique = true)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private AdminSessionCloseReason revokedReason;

    @Column(name = "revoked_by_session_id", columnDefinition = "uuid")
    private UUID revokedBySessionId;

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

    public String getSessionState() {
        return sessionState;
    }

    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public AdminSessionCloseReason getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(AdminSessionCloseReason revokedReason) {
        this.revokedReason = revokedReason;
    }

    public UUID getRevokedBySessionId() {
        return revokedBySessionId;
    }

    public void setRevokedBySessionId(UUID revokedBySessionId) {
        this.revokedBySessionId = revokedBySessionId;
    }
}
