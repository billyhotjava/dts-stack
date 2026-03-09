package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Rollback audit log entity.
 * Records every rollback operation for traceability.
 */
@Entity
@Table(name = "rollback_audit_log")
public class RollbackAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Size(max = 128)
	@Column(name = "operator", length = 128)
	private String operator;

	@Column(name = "level")
	private int level;

	@Size(max = 32)
	@Column(name = "scope", length = 32)
	private String scope;

	@Column(name = "task_id")
	private Long taskId;

	@Column(name = "data_source_id")
	private UUID dataSourceId;

	@Column(name = "request_json", columnDefinition = "TEXT")
	private String requestJson;

	@Column(name = "impact_json", columnDefinition = "TEXT")
	private String impactJson;

	@Column(name = "result_json", columnDefinition = "TEXT")
	private String resultJson;

	@Size(max = 32)
	@Column(name = "status", length = 32)
	private String status = "SUCCESS";

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "created_at")
	private Instant createdAt = Instant.now();

	// Getters and Setters

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public UUID getDataSourceId() {
		return dataSourceId;
	}

	public void setDataSourceId(UUID dataSourceId) {
		this.dataSourceId = dataSourceId;
	}

	public String getRequestJson() {
		return requestJson;
	}

	public void setRequestJson(String requestJson) {
		this.requestJson = requestJson;
	}

	public String getImpactJson() {
		return impactJson;
	}

	public void setImpactJson(String impactJson) {
		this.impactJson = impactJson;
	}

	public String getResultJson() {
		return resultJson;
	}

	public void setResultJson(String resultJson) {
		this.resultJson = resultJson;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RollbackAuditLog)) return false;
		RollbackAuditLog that = (RollbackAuditLog) o;
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public String toString() {
		return "RollbackAuditLog{" +
			"id=" + id +
			", operator='" + operator + '\'' +
			", level=" + level +
			", scope='" + scope + '\'' +
			", status='" + status + '\'' +
			", createdAt=" + createdAt +
			'}';
	}
}
