package com.yuzhi.dts.metrics.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MetricsFrontendResource {

    @GetMapping(
        {
            "/metrics",
            "/metrics/",
            "/metrics/operations",
            "/metrics/center",
            "/metrics/dictionary",
            "/metrics/packs",
            "/metrics/semantic",
            "/metrics/semantic/subjects",
            "/metrics/semantic/objects",
            "/metrics/semantic/metrics",
            "/metrics/semantic/models",
            "/metrics/semantic/publish",
            "/metrics/semantic/runs",
        }
    )
    public String frontend() {
        return "forward:/metrics/index.html";
    }
}
