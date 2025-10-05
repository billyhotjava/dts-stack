package com.yuzhi.dts.platform.service.query;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(QueryGateway.class)
public class NoopQueryGateway implements QueryGateway {

    private static final Logger log = LoggerFactory.getLogger(NoopQueryGateway.class);

    @Override
    public Map<String, Object> execute(String effectiveSql) {
        log.info("Simulate execute SQL: {}", effectiveSql);
        // Simulate a small result set
        int cols = 5;
        int rows = 10;
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < cols; i++) headers.add("col_" + (i + 1));
        List<Map<String, Object>> data = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < rows; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String h : headers) row.put(h, r.nextInt(1, 1000));
            data.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("rows", data);
        result.put("durationMs", r.nextInt(50, 400));
        result.put("effectiveSql", effectiveSql);
        return result;
    }
}
