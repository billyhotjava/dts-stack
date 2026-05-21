package com.yuzhi.dts.opmanager.web.rest;

import com.yuzhi.dts.opmanager.configfiles.ConfigApplyRequest;
import com.yuzhi.dts.opmanager.configfiles.ConfigApplyResult;
import com.yuzhi.dts.opmanager.configfiles.ConfigLineApplyRequest;
import com.yuzhi.dts.opmanager.configfiles.ConfigPrecheckRequest;
import com.yuzhi.dts.opmanager.configfiles.ConfigPrecheckResponse;
import com.yuzhi.dts.opmanager.configfiles.ProtectedConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opmanager/config")
public class ConfigResource {

    private final ProtectedConfigService protectedConfigService;

    public ConfigResource(ProtectedConfigService protectedConfigService) {
        this.protectedConfigService = protectedConfigService;
    }

    @PostMapping("/precheck")
    public ConfigPrecheckResponse precheck(@Valid @RequestBody ConfigPrecheckRequest request) {
        return protectedConfigService.precheck(request.packageRegistrationId());
    }

    @PostMapping("/apply")
    public ConfigApplyResult apply(@Valid @RequestBody ConfigApplyRequest request) {
        return protectedConfigService.apply(request.packageRegistrationId(), request.path(), request.action());
    }

    @PostMapping("/apply-line")
    public ConfigApplyResult applyLine(@Valid @RequestBody ConfigLineApplyRequest request) {
        return protectedConfigService.applyPackageLine(
            request.packageRegistrationId(),
            request.path(),
            request.localLineNumber(),
            request.packageLineNumber(),
            request.insertAfterLocalLineNumber(),
            request.expectedLocalText(),
            request.expectedPackageText()
        );
    }
}
