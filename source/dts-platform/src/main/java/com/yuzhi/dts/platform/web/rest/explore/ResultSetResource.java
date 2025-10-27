package com.yuzhi.dts.platform.web.rest.explore;

import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public ApiResponse<List<ResultSet>> list() {
        String current = resolveCurrentUsername();
        if (current == null) {
            return ApiResponses.ok(List.of());
        }
        return ApiResponses.ok(repository.findByCreatedByOrderByCreatedDateDesc(current));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResultSet> get(@PathVariable UUID id) {
        return repository
            .findById(id)
            .map(rs -> {
                assertResultSetAccess(rs);
                return ApiResponses.ok(rs);
            })
            .orElseGet(() -> ApiResponses.ok(null));
    }

    @PostMapping
    public ApiResponse<ResultSet> create(@RequestBody ResultSet item) { return ApiResponses.ok(repository.save(item)); }

    @PutMapping("/{id}")
    public ApiResponse<ResultSet> update(@PathVariable UUID id, @RequestBody ResultSet item) {
        ResultSet existing = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "结果集不存在"));
        assertResultSetAccess(existing);
        item.setId(id);
        item.setCreatedBy(existing.getCreatedBy());
        item.setCreatedDate(existing.getCreatedDate());
        ResultSet saved = repository.save(item);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        ResultSet rs = repository.findById(id).orElse(null);
        if (rs == null) {
            return ApiResponses.ok(Boolean.TRUE);
        }
        assertResultSetAccess(rs);
        executionRepository.clearResultSetReferences(id);
        repository.deleteById(id);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanupExpired() {
        if (!canManageAllResultSets()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅运维管理员可以批量清理结果集");
        }
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

    private boolean canManageAllResultSets() {
        return SecurityUtils.isOpAdminAccount() ||
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
    }

    private String resolveCurrentUsername() {
        return SecurityUtils
            .getCurrentUserLogin()
            .map(name -> name == null ? null : name.trim())
            .filter(name -> name != null && !name.isEmpty())
            .orElse(null);
    }

    private boolean isResultSetOwner(ResultSet rs) {
        if (rs == null || rs.getCreatedBy() == null) {
            return false;
        }
        String owner = rs.getCreatedBy().trim();
        if (owner.isEmpty()) {
            return false;
        }
        String current = resolveCurrentUsername();
        return current != null && owner.equalsIgnoreCase(current);
    }

    private void assertResultSetAccess(ResultSet rs) {
        if (isResultSetOwner(rs)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前结果集仅创建人可访问");
    }
}
