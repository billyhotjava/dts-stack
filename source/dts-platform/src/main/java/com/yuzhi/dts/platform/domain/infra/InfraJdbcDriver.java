package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(
    name = "infra_jdbc_driver",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_infra_jdbc_driver_file", columnNames = { "file_name" })
    }
)
public class InfraJdbcDriver extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "file_name", length = 256, nullable = false)
    private String fileName;

    @Column(name = "file_path", length = 1024, nullable = false)
    private String filePath;

    @Column(name = "driver_class", length = 256)
    private String driverClass;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "jdk_spec", length = 64)
    private String jdkSpec;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getJdkSpec() {
        return jdkSpec;
    }

    public void setJdkSpec(String jdkSpec) {
        this.jdkSpec = jdkSpec;
    }
}
