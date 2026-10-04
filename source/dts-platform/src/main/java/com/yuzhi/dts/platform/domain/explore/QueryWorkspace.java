package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "query_workspace")
public class QueryWorkspace extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "default_connection", length = 64)
    private String defaultConnection;

    @Column(name = "variables", length = 2048)
    private String variables;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDefaultConnection() { return defaultConnection; }
    public void setDefaultConnection(String defaultConnection) { this.defaultConnection = defaultConnection; }
    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }
}

