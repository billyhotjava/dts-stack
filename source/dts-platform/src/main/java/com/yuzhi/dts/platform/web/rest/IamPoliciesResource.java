package com.yuzhi.dts.platform.web.rest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/policies")
public class IamPoliciesResource {

    @GetMapping("/domains-with-datasets")
    public ApiResponse<List<Map<String, Object>>> domainsWithDatasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int d = 1; d <= 3; d++) {
            List<Map<String, Object>> datasets = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                datasets.add(Map.of("id", "ds-" + d + "-" + i, "name", "dataset_" + d + "_" + i, "fields", List.of("id", "name", "level")));
            }
            out.add(Map.of("id", "dom-" + d, "name", "domain_" + d, "datasets", datasets));
        }
        return ApiResponses.ok(out);
    }

    @GetMapping("/dataset/{datasetId}/policies")
    public ApiResponse<Map<String, Object>> datasetPolicies(@PathVariable String datasetId) {
        List<Map<String, Object>> objectPolicies = List.of(
            Map.of("subjectType", "ROLE", "subjectId", "r1", "subjectName", "DATA_VIEWER", "effect", "ALLOW", "source", "MANUAL"),
            Map.of("subjectType", "ROLE", "subjectId", "r2", "subjectName", "EXTERNAL", "effect", "DENY", "source", "SYSTEM")
        );
        List<Map<String, Object>> fieldPolicies = List.of(Map.of("field", "email", "subjectType", "ROLE", "subjectName", "DATA_VIEWER", "effect", "DENY"));
        List<Map<String, Object>> rowConds = List.of(Map.of("subjectType", "ROLE", "subjectName", "DATA_VIEWER", "expression", "level in ('公开','内部')"));
        return ApiResponses.ok(Map.of("objectPolicies", objectPolicies, "fieldPolicies", fieldPolicies, "rowConditions", rowConds));
    }

    @GetMapping("/subject/{type}/{id}/visible")
    public ApiResponse<Map<String, Object>> subjectVisible(@PathVariable String type, @PathVariable String id) {
        return ApiResponses.ok(Map.of(
            "objects",
            List.of(Map.of("datasetId", "ds-1-1", "datasetName", "dataset_1_1", "effect", "ALLOW")),
            "fields",
            List.of(Map.of("datasetName", "dataset_1_1", "field", "email", "effect", "DENY")),
            "expressions",
            List.of(Map.of("datasetName", "dataset_1_1", "expression", "level in ('公开','内部')"))
        ));
    }

    @GetMapping("/subjects")
    public ApiResponse<List<Map<String, String>>> searchSubjects(@RequestParam String type, @RequestParam String keyword) {
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 1; i <= 5; i++) out.add(Map.of("id", type.substring(0, 1) + i, "name", keyword + "_" + i));
        return ApiResponses.ok(out);
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        return ApiResponses.ok(Map.of("conflicts", List.of()));
    }

    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        return ApiResponses.ok(Map.of("ok", Boolean.TRUE, "appliedAt", Instant.now().toString()));
    }
}
