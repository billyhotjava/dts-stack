package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.WordRootContract.UpsertRequest;
import com.yuzhi.dts.platform.service.modeling.WordRootContract.View;
import com.yuzhi.dts.platform.service.modeling.WordRootService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/word-roots")
public class ModelingWordRootResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final WordRootService service;
    private final AuditService audit;

    public ModelingWordRootResource(WordRootService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<View>> list(
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        List<View> roots = service.list(keyword, activeDeptHeader);
        audit.auditAction("MODELING_WORD_ROOT_LIST", AuditStage.SUCCESS, "list", Map.of("count", roots.size()));
        return ApiResponses.ok(roots);
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<View> create(
        @Valid @RequestBody UpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        View created = service.create(request, activeDeptHeader);
        audit.auditAction("MODELING_WORD_ROOT_EDIT", AuditStage.SUCCESS, created.id().toString(), Map.of("summary", "新建词根"));
        return ApiResponses.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<View> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDeptHeader
    ) {
        View updated = service.update(id, request, activeDeptHeader);
        audit.auditAction("MODELING_WORD_ROOT_EDIT", AuditStage.SUCCESS, id.toString(), Map.of("summary", "更新词根"));
        return ApiResponses.ok(updated);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateCode(DataIntegrityViolationException exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), "词根编码已存在，请勿重复创建", "WORD_ROOT_CODE_DUPLICATE", null));
    }
}
