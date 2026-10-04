package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalDirectory;
import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalItem;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.DataPortalService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-portal")
@Transactional
public class DataPortalResource {

    private final AnalyticsSessionService sessionService;
    private final DataPortalService dataPortalService;

    public DataPortalResource(
            AnalyticsSessionService sessionService,
            DataPortalService dataPortalService) {
        this.sessionService = sessionService;
        this.dataPortalService = dataPortalService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(HttpServletRequest request) {
        Optional<AnalyticsUser> maybeUser = MetabaseAuth.currentUser(sessionService, request);
        if (maybeUser.isEmpty()) {
            return unauthenticated();
        }
        AnalyticsUser user = maybeUser.orElseThrow();
        DataPortalService.Snapshot snapshot = dataPortalService.snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("can_write", user.isSuperuser() || MetabaseAuth.isDataAdmin(request));
        body.put("directories", snapshot.directories().stream().map(DataPortalResource::toDirectory).toList());
        body.put("items", snapshot.items().stream().map(DataPortalResource::toItem).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping(path = "/directories", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDirectory(
            @RequestBody DirectoryRequest body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> denied = MetabaseAuth.requireDataAdmin(sessionService, request);
        if (denied.isPresent()) {
            return denied.orElseThrow();
        }
        AnalyticsUser user = MetabaseAuth.currentUser(sessionService, request).orElseThrow();
        AnalyticsDataPortalDirectory created = dataPortalService.createDirectory(
                body == null ? null : body.name(),
                body == null ? null : body.parentId(),
                user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDirectory(created));
    }

    @PutMapping(path = "/directories/{directoryId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateDirectory(
            @PathVariable Long directoryId,
            @RequestBody DirectoryRequest body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> denied = MetabaseAuth.requireDataAdmin(sessionService, request);
        if (denied.isPresent()) {
            return denied.orElseThrow();
        }
        AnalyticsDataPortalDirectory updated = dataPortalService.updateDirectory(
                directoryId,
                body == null ? null : body.name(),
                body == null ? null : body.parentId());
        return ResponseEntity.ok(toDirectory(updated));
    }

    @DeleteMapping(path = "/directories/{directoryId}")
    public ResponseEntity<?> deleteDirectory(
            @PathVariable Long directoryId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> denied = MetabaseAuth.requireDataAdmin(sessionService, request);
        if (denied.isPresent()) {
            return denied.orElseThrow();
        }
        dataPortalService.deleteDirectory(directoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createItem(
            @RequestBody ItemRequest body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> denied = MetabaseAuth.requireDataAdmin(sessionService, request);
        if (denied.isPresent()) {
            return denied.orElseThrow();
        }
        AnalyticsUser user = MetabaseAuth.currentUser(sessionService, request).orElseThrow();
        AnalyticsDataPortalItem created = dataPortalService.createItem(
                body == null ? null : body.directoryId(),
                body == null ? null : body.contentType(),
                body == null ? null : body.contentId(),
                user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toItem(created));
    }

    @DeleteMapping(path = "/items/{itemId}")
    public ResponseEntity<?> deleteItem(
            @PathVariable Long itemId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> denied = MetabaseAuth.requireDataAdmin(sessionService, request);
        if (denied.isPresent()) {
            return denied.orElseThrow();
        }
        dataPortalService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> toDirectory(AnalyticsDataPortalDirectory directory) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", directory.getId());
        item.put("name", directory.getName());
        item.put("parent_id", directory.getParentId());
        item.put("sort_order", directory.getSortOrder());
        item.put("version_no", directory.getVersionNo());
        item.put("created_at", directory.getCreatedAt());
        item.put("updated_at", directory.getUpdatedAt());
        return item;
    }

    private static Map<String, Object> toItem(AnalyticsDataPortalItem binding) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", binding.getId());
        item.put("directory_id", binding.getDirectoryId());
        item.put("content_type", binding.getContentType());
        item.put("content_id", binding.getContentId());
        item.put("sort_order", binding.getSortOrder());
        item.put("created_at", binding.getCreatedAt());
        return item;
    }

    private static ResponseEntity<String> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Unauthenticated");
    }

    public record DirectoryRequest(
            @JsonProperty("name") String name,
            @JsonProperty("parent_id") Long parentId) {}

    public record ItemRequest(
            @JsonProperty("directory_id") Long directoryId,
            @JsonProperty("content_type") String contentType,
            @JsonProperty("content_id") Long contentId) {}
}
