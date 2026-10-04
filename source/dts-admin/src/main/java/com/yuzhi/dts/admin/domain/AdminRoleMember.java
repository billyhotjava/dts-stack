package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "admin_role_member")
public class AdminRoleMember extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "role", nullable = false, length = 255)
    private String role;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /**
     * F11-T02：稳定账号键（Keycloak kc_id）。双写过渡期可空；回填与切换由 T08 执行，
     * 在此之前 username 仍是生效键。本列只增不改历史语义。
     */
    @Column(name = "keycloak_id", length = 64)
    private String keycloakId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

