package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "sql_connection")
public class SqlConnection extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine", nullable = false, length = 16)
    private ExecEnums.ExecEngine engine;

    @Column(name = "catalog", length = 128)
    private String catalog;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(name = "props", length = 2048)
    private String props;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ExecEnums.ExecEngine getEngine() { return engine; }
    public void setEngine(ExecEnums.ExecEngine engine) { this.engine = engine; }
    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getProps() { return props; }
    public void setProps(String props) { this.props = props; }
}

