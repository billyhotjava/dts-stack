package com.yuzhi.dts.platform.web.rest.explore;

import com.yuzhi.dts.platform.domain.explore.SqlConnection;
import com.yuzhi.dts.platform.repository.explore.SqlConnectionRepository;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sql-connections")
@Transactional
public class SqlConnectionResource {

    private final SqlConnectionRepository repository;

    public SqlConnectionResource(SqlConnectionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<SqlConnection>> list() { return ApiResponses.ok(repository.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<SqlConnection> get(@PathVariable UUID id) { return ApiResponses.ok(repository.findById(id).orElse(null)); }

    @PostMapping
    public ApiResponse<SqlConnection> create(@RequestBody SqlConnection item) { return ApiResponses.ok(repository.save(item)); }

    @PutMapping("/{id}")
    public ApiResponse<SqlConnection> update(@PathVariable UUID id, @RequestBody SqlConnection item) {
        item.setId(id);
        return ApiResponses.ok(repository.save(item));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        repository.deleteById(id);
        return ApiResponses.ok(Boolean.TRUE);
    }
}

