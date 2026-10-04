package com.yuzhi.dts.opmanager.web.rest;

import com.yuzhi.dts.opmanager.runtime.RuntimeInspector;
import com.yuzhi.dts.opmanager.runtime.RuntimeStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opmanager/runtime")
public class RuntimeResource {

    private final RuntimeInspector runtimeInspector;

    public RuntimeResource(RuntimeInspector runtimeInspector) {
        this.runtimeInspector = runtimeInspector;
    }

    @GetMapping
    public RuntimeStatus runtime() {
        return runtimeInspector.inspect();
    }
}
