package com.yuzhi.dts.platform.web.rest.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/asset-permission")
public class AssetPermissionV1InternalResource {

    private final AssetPermissionInternalResource delegate;

    public AssetPermissionV1InternalResource(AssetPermissionInternalResource delegate) {
        this.delegate = delegate;
    }

    @PostMapping("/policy")
    public ResponseEntity<AssetPermissionInternalResource.PolicyResponse> policy(
        @RequestBody AssetPermissionInternalResource.CheckRequest request
    ) {
        return delegate.policyV1(request);
    }
}
