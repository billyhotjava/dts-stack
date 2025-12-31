package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/field")
public class FieldResource {

    private final AnalyticsSessionService sessionService;

    public FieldResource(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping(path = "/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody Object ignored, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/{fieldId}/values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> values(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping(path = "/{fieldId}/values", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> valuesUpdate(@PathVariable("fieldId") long fieldId, @RequestBody Object ignored, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{fieldId}/dimension", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateDimension(@PathVariable("fieldId") long fieldId, @RequestBody Object ignored, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{fieldId}/dimension")
    public ResponseEntity<?> deleteDimension(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{fieldId}/rescan_values")
    public ResponseEntity<?> rescan(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{fieldId}/discard_values")
    public ResponseEntity<?> discard(@PathVariable("fieldId") long fieldId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/{fieldId}/search/{searchFieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(
            @PathVariable("fieldId") long fieldId,
            @PathVariable("searchFieldId") long searchFieldId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{fieldId}/remapping/{remappedFieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> remapping(
            @PathVariable("fieldId") long fieldId,
            @PathVariable("remappedFieldId") long remappedFieldId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(new java.util.LinkedHashMap<>());
    }
}
