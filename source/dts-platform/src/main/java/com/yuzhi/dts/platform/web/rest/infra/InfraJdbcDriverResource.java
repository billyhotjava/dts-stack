package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.service.infra.InfraJdbcDriverService;
import com.yuzhi.dts.platform.service.infra.dto.InfraJdbcDriverDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraJdbcDriverUpdateRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/infra/jdbc-drivers")
public class InfraJdbcDriverResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final InfraJdbcDriverService driverService;

    public InfraJdbcDriverResource(InfraJdbcDriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<List<InfraJdbcDriverDto>> list() {
        return ApiResponses.ok(driverService.listDrivers());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraJdbcDriverDto> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponses.ok(driverService.upload(file));
    }

    @PutMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraJdbcDriverDto> update(@PathVariable UUID id, @Valid @RequestBody InfraJdbcDriverUpdateRequest request) {
        return ApiResponses.ok(driverService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        driverService.delete(id);
        return ApiResponses.ok(null);
    }
}
