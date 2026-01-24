package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.service.infra.OdsTableMappingService;
import com.yuzhi.dts.platform.service.infra.OdsTableMappingService.OdsMappingRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/infra/ods-mappings")
public class OdsTableMappingResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final OdsTableMappingService service;

    public OdsTableMappingResource(OdsTableMappingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<InfraOdsTableMapping>> list(@RequestParam(name = "connectionId", required = false) UUID connectionId) {
        return ApiResponses.ok(service.list(connectionId));
    }

    @PostMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<List<InfraOdsTableMapping>> upsert(@RequestBody List<OdsMappingRequest> requests) {
        return ApiResponses.ok(service.upsertBatch(requests));
    }

    @PutMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraOdsTableMapping> update(@PathVariable UUID id, @RequestBody OdsMappingRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        OdsMappingRequest patched = new OdsMappingRequest(
            id,
            request.connectionId(),
            request.streamName(),
            request.streamNamespace(),
            request.systemCode(),
            request.bizCode(),
            request.entityCode(),
            request.odsSchema(),
            request.owner(),
            request.ownerDept(),
            request.description(),
            request.enabled(),
            request.datasetId()
        );
        return ApiResponses.ok(service.upsert(patched));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponses.ok(Boolean.TRUE);
    }
}
