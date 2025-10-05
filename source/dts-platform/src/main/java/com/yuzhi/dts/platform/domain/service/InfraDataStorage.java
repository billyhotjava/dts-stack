package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_data_storage")
public class InfraDataStorage extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "type", length = 32)
    private String type; // hdfs/s3/minio/hive

    @Column(name = "location", length = 512)
    private String location; // URI or path

    @Column(name = "props", length = 2048)
    private String props;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "secure_props", columnDefinition = "bytea")
    private byte[] secureProps;

    @Column(name = "secure_iv", columnDefinition = "bytea")
    private byte[] secureIv;

    @Column(name = "secure_key_version", length = 32)
    private String secureKeyVersion;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getProps() { return props; }
    public void setProps(String props) { this.props = props; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public byte[] getSecureProps() { return secureProps; }
    public void setSecureProps(byte[] secureProps) { this.secureProps = secureProps; }
    public byte[] getSecureIv() { return secureIv; }
    public void setSecureIv(byte[] secureIv) { this.secureIv = secureIv; }
    public String getSecureKeyVersion() { return secureKeyVersion; }
    public void setSecureKeyVersion(String secureKeyVersion) { this.secureKeyVersion = secureKeyVersion; }
}
