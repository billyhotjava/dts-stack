package com.yuzhi.dts.ingestion.service.etl.connector;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SourceConnectorRegistry {

    private final List<SourceConnector> connectors;

    public SourceConnectorRegistry(List<SourceConnector> connectors) {
        this.connectors = connectors == null ? List.of() : List.copyOf(connectors);
    }

    public List<String> connectorTypes() {
        return connectors.stream().map(SourceConnector::connectorType).distinct().sorted().toList();
    }

    public Optional<SourceConnector> find(SourceConnectorContext context) {
        return connectors.stream().filter(connector -> connector.supports(context)).findFirst();
    }
}

