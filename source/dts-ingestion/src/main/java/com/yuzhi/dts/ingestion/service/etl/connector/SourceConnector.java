package com.yuzhi.dts.ingestion.service.etl.connector;

public interface SourceConnector {
    String connectorType();

    boolean supports(SourceConnectorContext context);

    void validate(SourceConnectorContext context);

    ExecutionPlan buildExecutionPlan(SourceConnectorContext context);
}

