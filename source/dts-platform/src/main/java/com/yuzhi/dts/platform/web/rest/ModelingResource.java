package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.modeling.ModelingStandard;
import com.yuzhi.dts.platform.repository.modeling.ModelingStandardRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modeling")
@Transactional
public class ModelingResource {

    private final ModelingStandardRepository repo;
    private final AuditService audit;

    public ModelingResource(ModelingStandardRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @GetMapping("/standards")
    public ApiResponse<List<ModelingStandard>> list() {
        List<ModelingStandard> list = repo.findAll();
        audit.audit("READ", "modeling.standard", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/standards")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<ModelingStandard> create(@Valid @RequestBody ModelingStandard item) {
        ModelingStandard saved = repo.save(item);
        audit.audit("CREATE", "modeling.standard", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/standards/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<ModelingStandard> update(@PathVariable UUID id, @Valid @RequestBody ModelingStandard patch) {
        ModelingStandard existing = repo.findById(id).orElseThrow();
        existing.setCategory(patch.getCategory());
        existing.setKey(patch.getKey());
        existing.setValue(patch.getValue());
        existing.setDescription(patch.getDescription());
        ModelingStandard saved = repo.save(existing);
        audit.audit("UPDATE", "modeling.standard", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/standards/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        repo.deleteById(id);
        audit.audit("DELETE", "modeling.standard", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }
}

