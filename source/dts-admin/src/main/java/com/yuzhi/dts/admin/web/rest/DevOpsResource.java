package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.config.DevDataSeeder;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dev")
@Profile("dev")
public class DevOpsResource {

    private final DevDataSeeder seeder;

    public DevOpsResource(DevDataSeeder seeder) {
        this.seeder = seeder;
    }

    @PostMapping("/seed")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.AUTH_ADMIN + "','" + AuthoritiesConstants.AUDITOR_ADMIN + "')")
    public ResponseEntity<ApiResponse<String>> triggerSeed() {
        seeder.seedAll();
        return ResponseEntity.ok(ApiResponse.ok("seeded"));
    }
}

