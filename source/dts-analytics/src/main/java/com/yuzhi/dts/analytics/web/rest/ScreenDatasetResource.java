package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenDataset;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.ScreenDatasetService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/screen-datasets")
public class ScreenDatasetResource {

    private final ScreenDatasetService service;
    private final AnalyticsSessionService sessionService;
    private final ObjectMapper objectMapper;

    public ScreenDatasetResource(ScreenDatasetService service,
            AnalyticsSessionService sessionService,
            ObjectMapper objectMapper) {
        this.service = service;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        String name = body.path("name").asText("未命名数据集");
        String originalFileName = body.path("originalFileName").asText("");
        String fileType = body.path("fileType").asText("");
        JsonNode columnsMeta = body.path("columnsMeta");
        JsonNode rowsData = body.path("rows");
        int rowCount = rowsData.isArray() ? rowsData.size() : 0;
        long fileSize = body.path("fileSize").asLong(0);
        Long userId = user.get().getId();

        AnalyticsScreenDataset ds = service.create(
                name, originalFileName, fileType,
                columnsMeta.toString(), rowsData.toString(),
                rowCount, fileSize, userId);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("uuid", ds.getUuid());
        result.put("name", ds.getName());
        result.put("rowCount", ds.getRowCount());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        List<AnalyticsScreenDataset> datasets = service.listAll();
        var arr = objectMapper.createArrayNode();
        for (var ds : datasets) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("uuid", ds.getUuid());
            node.put("name", ds.getName());
            node.put("originalFileName", ds.getOriginalFileName());
            node.put("fileType", ds.getFileType());
            node.set("columnsMeta", parseJson(ds.getColumnsMeta()));
            node.put("rowCount", ds.getRowCount());
            node.put("fileSize", ds.getFileSize());
            node.put("createdAt", ds.getCreatedAt() != null ? ds.getCreatedAt().toString() : "");
            arr.add(node);
        }
        return ResponseEntity.ok(arr);
    }

    @GetMapping("/{uuid}/data")
    public ResponseEntity<?> getData(@PathVariable String uuid, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreenDataset ds = service.getByUuid(uuid);
        String rowsJson = service.decryptData(ds);

        ObjectNode result = objectMapper.createObjectNode();
        result.set("cols", parseJson(ds.getColumnsMeta()));
        result.set("rows", parseJson(rowsJson));
        result.put("rowCount", ds.getRowCount());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{uuid}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable String uuid, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        service.deleteByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
    }
}
