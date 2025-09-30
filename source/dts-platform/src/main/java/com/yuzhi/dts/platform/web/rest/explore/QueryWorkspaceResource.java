package com.yuzhi.dts.platform.web.rest.explore;

import com.yuzhi.dts.platform.domain.explore.QueryWorkspace;
import com.yuzhi.dts.platform.repository.explore.QueryWorkspaceRepository;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query-workspaces")
@Transactional
public class QueryWorkspaceResource {

    private final QueryWorkspaceRepository repository;

    public QueryWorkspaceResource(QueryWorkspaceRepository repository) { this.repository = repository; }

    @GetMapping
    public ApiResponse<List<QueryWorkspace>> list() { return ApiResponses.ok(repository.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<QueryWorkspace> get(@PathVariable UUID id) { return ApiResponses.ok(repository.findById(id).orElse(null)); }

    @PostMapping
    public ApiResponse<QueryWorkspace> create(@RequestBody QueryWorkspace item) { return ApiResponses.ok(repository.save(item)); }

    @PutMapping("/{id}")
    public ApiResponse<QueryWorkspace> update(@PathVariable UUID id, @RequestBody QueryWorkspace item) {
        item.setId(id);
        return ApiResponses.ok(repository.save(item));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        repository.deleteById(id);
        return ApiResponses.ok(Boolean.TRUE);
    }
}

