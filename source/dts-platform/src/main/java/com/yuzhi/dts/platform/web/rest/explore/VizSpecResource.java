package com.yuzhi.dts.platform.web.rest.explore;

import com.yuzhi.dts.platform.domain.explore.VizSpec;
import com.yuzhi.dts.platform.repository.explore.VizSpecRepository;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/viz-specs")
@Transactional
public class VizSpecResource {

    private final VizSpecRepository repository;

    public VizSpecResource(VizSpecRepository repository) { this.repository = repository; }

    @GetMapping
    public ApiResponse<List<VizSpec>> list() { return ApiResponses.ok(repository.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<VizSpec> get(@PathVariable UUID id) { return ApiResponses.ok(repository.findById(id).orElse(null)); }

    @PostMapping
    public ApiResponse<VizSpec> create(@RequestBody VizSpec item) { return ApiResponses.ok(repository.save(item)); }

    @PutMapping("/{id}")
    public ApiResponse<VizSpec> update(@PathVariable UUID id, @RequestBody VizSpec item) {
        item.setId(id);
        return ApiResponses.ok(repository.save(item));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        repository.deleteById(id);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/{id}/render")
    public ApiResponse<Map<String, Object>> render(@PathVariable UUID id) {
        // Minimal stub: returns stored config for frontend rendering
        var spec = repository.findById(id).orElse(null);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("config", spec != null ? spec.getConfig() : null);
        return ApiResponses.ok(resp);
    }
}

