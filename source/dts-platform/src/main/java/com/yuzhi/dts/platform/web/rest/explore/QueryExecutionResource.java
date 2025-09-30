package com.yuzhi.dts.platform.web.rest.explore;

import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query-executions")
@Transactional
public class QueryExecutionResource {

    private final QueryExecutionRepository repository;

    public QueryExecutionResource(QueryExecutionRepository repository) { this.repository = repository; }

    @GetMapping
    public ApiResponse<List<QueryExecution>> list() { return ApiResponses.ok(repository.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<QueryExecution> get(@PathVariable UUID id) { return ApiResponses.ok(repository.findById(id).orElse(null)); }

    @PostMapping
    public ApiResponse<QueryExecution> create(@RequestBody QueryExecution item) { return ApiResponses.ok(repository.save(item)); }

    @PutMapping("/{id}")
    public ApiResponse<QueryExecution> update(@PathVariable UUID id, @RequestBody QueryExecution item) {
        item.setId(id);
        return ApiResponses.ok(repository.save(item));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        repository.deleteById(id);
        return ApiResponses.ok(Boolean.TRUE);
    }
}

