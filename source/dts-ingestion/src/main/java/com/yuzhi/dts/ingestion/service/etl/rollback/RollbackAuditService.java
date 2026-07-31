package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.yuzhi.dts.ingestion.domain.RollbackAuditLog;
import com.yuzhi.dts.ingestion.repository.RollbackAuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackAuditService {

	private final RollbackAuditLogRepository repository;

	public RollbackAuditService(RollbackAuditLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public RollbackAuditLog record(String operator, RollbackLevel level, String scope,
									Long taskId, UUID dataSourceId,
									String requestJson, String impactJson,
									String resultJson, String status, String errorMessage) {
		RollbackAuditLog log = new RollbackAuditLog();
		log.setOperator(operator);
		log.setLevel(level.code());
		log.setScope(scope);
		log.setTaskId(taskId);
		log.setDataSourceId(dataSourceId);
		log.setRequestJson(requestJson);
		log.setImpactJson(impactJson);
		log.setResultJson(resultJson);
		log.setStatus(status);
		log.setErrorMessage(errorMessage);
		return repository.save(log);
	}

	public List<RollbackAuditLog> findByTaskId(Long taskId) {
		return repository.findByTaskIdOrderByCreatedAtDesc(taskId);
	}

	public List<RollbackAuditLog> findByDataSourceId(UUID dataSourceId) {
		return repository.findByDataSourceIdOrderByCreatedAtDesc(dataSourceId);
	}
}
