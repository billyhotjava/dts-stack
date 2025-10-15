package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_dataset_grant")
public class CatalogDatasetGrant extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private CatalogDataset dataset;

    @Column(name = "grantee_id", length = 64)
    private String granteeId;

    @NotBlank
    @Column(name = "grantee_username", length = 64)
    private String granteeUsername;

    @Column(name = "grantee_name", length = 128)
    private String granteeName;

    @Column(name = "grantee_dept", length = 64)
    private String granteeDept;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CatalogDataset getDataset() {
        return dataset;
    }

    public void setDataset(CatalogDataset dataset) {
        this.dataset = dataset;
    }

    public String getGranteeId() {
        return granteeId;
    }

    public void setGranteeId(String granteeId) {
        this.granteeId = granteeId;
    }

    public String getGranteeUsername() {
        return granteeUsername;
    }

    public void setGranteeUsername(String granteeUsername) {
        this.granteeUsername = granteeUsername;
    }

    public String getGranteeName() {
        return granteeName;
    }

    public void setGranteeName(String granteeName) {
        this.granteeName = granteeName;
    }

    public String getGranteeDept() {
        return granteeDept;
    }

    public void setGranteeDept(String granteeDept) {
        this.granteeDept = granteeDept;
    }
}
