package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.visualization.ReportRegistrationService;
import com.yuzhi.dts.platform.service.visualization.ReportRegistrationService.RegistrationResult;
import com.yuzhi.dts.platform.service.visualization.ReportRegistrationService.ReportRegistrationCommand;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/reports/registrations")
@PreAuthorize(
    "hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-analytics'"
)
public class ReportRegistrationInternalResource {

    private final ReportRegistrationService registrations;

    public ReportRegistrationInternalResource(ReportRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PutMapping
    public ApiResponse<RegistrationResult> register(@RequestBody ReportRegistrationCommand command) {
        return ApiResponses.ok(registrations.register(command));
    }
}
