package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.service.governance.QualityDatasetStatementExecutor.Validation;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QualityRulePreflightService {
    private final QualityDatasetReadGuard access;
    private final QualityDatasetStatementExecutor executor;
    public QualityRulePreflightService(QualityDatasetReadGuard access, QualityDatasetStatementExecutor executor) {
        this.access = access; this.executor = executor;
    }
    public Validation validate(Request request, String activeDept) {
        if (request == null || request.datasetId() == null) throw new IllegalArgumentException("请选择检测数据资产");
        access.requireReadable(request.datasetId(), activeDept);
        return executor.validate(request.datasetId(), QualityRuleStatements.resolve(request.definition()));
    }
    public Preview preview(Request request, String activeDept) {
        Validation validation = validate(request, activeDept);
        if (!validation.valid()) throw new Rejected(validation);
        GovQualityRun transientRun = new GovQualityRun();
        transientRun.setDatasetId(request.datasetId());
        transientRun.setTriggerType("DRY_RUN");
        var execution = executor.execute(transientRun, QualityRuleStatements.resolve(request.definition()));
        return new Preview(validation.checksum(), execution.outcome(), execution.rowsTotal(), execution.failingRowCount());
    }
    public void requireValid(UUID datasetId, Map<String, Object> definition, String activeDept) {
        Validation validation = validate(new Request(datasetId, definition), activeDept);
        if (!validation.valid()) throw new Rejected(validation);
    }
    public record Request(UUID datasetId, Map<String, Object> definition) {}
    public record Preview(String checksum, QualityExecutionOutcome outcome, Integer rowsTotal, Integer failingRowCount) {}
    public static final class Rejected extends RuntimeException {
        private final Validation validation;
        public Rejected(Validation validation) { super("检测 SQL 未通过校验"); this.validation = validation; }
        public Validation validation() { return validation; }
    }
}
