package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_data_product")
public class CatalogDataProduct extends AbstractAuditingEntity<UUID> implements Serializable {

	@Id
	@GeneratedValue
	@Column(name = "id", columnDefinition = "uuid")
	private UUID id;

	@Column(name = "name", length = 128, nullable = false)
	private String name;

	@Column(name = "code", length = 64, unique = true)
	private String code;

	@Column(name = "owner_dept", length = 64)
	private String ownerDept;

	@Column(name = "description", length = 2048)
	private String description;

	@Column(name = "dataset_ids", columnDefinition = "text")
	private String datasetIds;

	@Column(name = "indicator_codes", columnDefinition = "text")
	private String indicatorCodes;

	@Column(name = "classification", length = 32)
	private String classification;

	@Column(name = "freshness_sla", length = 64)
	private String freshnessSla;

	@Column(name = "lifecycle_status", length = 32)
	private String lifecycleStatus;

	@Column(name = "visibility", length = 32)
	private String visibility;

	@Column(name = "consumer_entry", length = 512)
	private String consumerEntry;

	@Column(name = "status", length = 32)
	private String status = "DRAFT";

	@Override
	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getOwnerDept() { return ownerDept; }
	public void setOwnerDept(String ownerDept) { this.ownerDept = ownerDept; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getDatasetIds() { return datasetIds; }
	public void setDatasetIds(String datasetIds) { this.datasetIds = datasetIds; }
	public String getIndicatorCodes() { return indicatorCodes; }
	public void setIndicatorCodes(String indicatorCodes) { this.indicatorCodes = indicatorCodes; }
	public String getClassification() { return classification; }
	public void setClassification(String classification) { this.classification = classification; }
	public String getFreshnessSla() { return freshnessSla; }
	public void setFreshnessSla(String freshnessSla) { this.freshnessSla = freshnessSla; }
	public String getLifecycleStatus() { return lifecycleStatus; }
	public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
	public String getVisibility() { return visibility; }
	public void setVisibility(String visibility) { this.visibility = visibility; }
	public String getConsumerEntry() { return consumerEntry; }
	public void setConsumerEntry(String consumerEntry) { this.consumerEntry = consumerEntry; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
}
