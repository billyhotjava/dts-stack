package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.MetricGraphDraftService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/graphs")
public class MetricGraphResource {

    private final MetricGraphDraftService graphDraftService;

    public MetricGraphResource(MetricGraphDraftService graphDraftService) {
        this.graphDraftService = graphDraftService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createDraft(@RequestBody Map<String, Object> graph) {
        return graphDraftService.createDraft(graph);
    }

    @GetMapping("/{draftId}")
    public Map<String, Object> getDraft(@PathVariable String draftId) {
        return graphDraftService.getDraft(draftId);
    }

    @PatchMapping("/{draftId}")
    public Map<String, Object> updateDraft(@PathVariable String draftId, @RequestBody Map<String, Object> graph) {
        return graphDraftService.updateDraft(draftId, graph);
    }

    @PostMapping("/draft/preflight")
    public Map<String, Object> preflightDraft(@RequestBody Map<String, Object> graph) {
        return graphDraftService.preflightDraft(graph);
    }

    @PostMapping("/{draftId}/preflight")
    public Map<String, Object> preflightStoredDraft(@PathVariable String draftId) {
        return graphDraftService.preflightStoredDraft(draftId);
    }
}
