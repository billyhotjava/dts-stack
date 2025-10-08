package com.yuzhi.dts.platform.domain.modeling;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "data_standard_attachment")
public class DataStandardAttachment extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_id", nullable = false)
    private DataStandard standard;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "file_name", length = 256, nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "file_size")
    private long fileSize;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "key_version", length = 32)
    private String keyVersion;

    @Column(name = "iv", columnDefinition = "bytea")
    private byte[] iv;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "cipher_blob", columnDefinition = "bytea")
    private byte[] cipherBlob;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DataStandard getStandard() {
        return standard;
    }

    public void setStandard(DataStandard standard) {
        this.standard = standard;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public byte[] getCipherBlob() {
        return cipherBlob;
    }

    public void setCipherBlob(byte[] cipherBlob) {
        this.cipherBlob = cipherBlob;
    }
}
