package com.yuzhi.dts.platform.web.rest.explore;

import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/result-sets")
@Transactional
public class ResultSetResource {

    private final ResultSetRepository repository;
    private final com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository executionRepository;
    private final com.yuzhi.dts.platform.service.audit.AuditService audit;

    public ResultSetResource(
        ResultSetRepository repository,
        com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository executionRepository,
        com.yuzhi.dts.platform.service.audit.AuditService audit
    ) {
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<ResultSet>> list() { return ApiResponses.ok(repository.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<ResultSet> get(@PathVariable UUID id) { return ApiResponses.ok(repository.findById(id).orElse(null)); }

    @PostMapping
    public ApiResponse<ResultSet> create(@RequestBody ResultSet item) { return ApiResponses.ok(repository.save(item)); }

    @PutMapping("/{id}")
    public ApiResponse<ResultSet> update(@PathVariable UUID id, @RequestBody ResultSet item) {
        item.setId(id);
        return ApiResponses.ok(repository.save(item));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        executionRepository.clearResultSetReferences(id);
        repository.deleteById(id);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanupExpired() {
        var now = java.time.Instant.now();
        var expired = repository.findByExpiresAtBefore(now);
        int count = 0;
        for (var rs : expired) {
            executionRepository.clearResultSetReferences(rs.getId());
            repository.deleteById(rs.getId());
            count++;
        }
        if (audit != null) audit.audit("DELETE", "explore.resultSet.cleanup", "count=" + count);
        return ApiResponses.ok(java.util.Map.of("deleted", count));
    }
}
