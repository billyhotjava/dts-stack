package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_data_source")
public class InfraDataSource extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "type", length = 32)
    private String type; // hive/jdbc/kafka/etc

    @Column(name = "jdbc_url", length = 512)
    private String jdbcUrl;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "props", length = 2048)
    private String props; // JSON or k=v

    @Column(name = "description", length = 512)
    private String description;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getProps() { return props; }
    public void setProps(String props) { this.props = props; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
