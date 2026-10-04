package com.yuzhi.dts.admin.web.rest.platform;

import com.yuzhi.dts.admin.service.infra.InfraAdminService;
import com.yuzhi.dts.admin.service.infra.dto.PlatformDataLakeConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.PlatformInceptorConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.PlatformDataLakeDestinationUpdateRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/infra")
public class PlatformInfraResource {

    private final InfraAdminService infraAdminService;

    public PlatformInfraResource(InfraAdminService infraAdminService) {
        this.infraAdminService = infraAdminService;
    }

    @GetMapping("/inceptor")
    public ResponseEntity<PlatformInceptorConfigResponse> currentInceptor() {
        return infraAdminService.currentPlatformInceptorConfig()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/data-lakes/default")
    public ResponseEntity<PlatformDataLakeConfigResponse> defaultDataLake() {
        return infraAdminService.currentPlatformDefaultDataLakeConfig()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/data-lakes/default/destination")
    public ResponseEntity<PlatformDataLakeConfigResponse> updateDefaultDestination(
        @RequestBody PlatformDataLakeDestinationUpdateRequest request
    ) {
        return infraAdminService.updateDefaultDataLakeDestination(request, "platform")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
