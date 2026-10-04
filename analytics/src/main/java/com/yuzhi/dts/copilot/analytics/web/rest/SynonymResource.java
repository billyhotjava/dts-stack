package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSynonym;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSynonymRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.Nl2SqlSemanticRecallService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/synonyms")
public class SynonymResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSynonymRepository synonymRepository;
    private final Nl2SqlSemanticRecallService semanticRecallService;

    public SynonymResource(
            AnalyticsSessionService sessionService,
            AnalyticsSynonymRepository synonymRepository,
            Nl2SqlSemanticRecallService semanticRecallService) {
        this.sessionService = sessionService;
        this.synonymRepository = synonymRepository;
        this.semanticRecallService = semanticRecallService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(synonymRepository.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        String term = body.path("term").asText(null);
        String columnName = body.path("columnName").asText(null);
        if (term == null || term.isBlank() || columnName == null || columnName.isBlank()) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "term and columnName are required");
            return ResponseEntity.badRequest().body(error);
        }

        AnalyticsSynonym synonym = new AnalyticsSynonym();
        synonym.setTerm(term.trim());
        synonym.setColumnName(columnName.trim());

        String tableHint = body.path("tableHint").asText(null);
        if (tableHint != null && !tableHint.isBlank()) {
            synonym.setTableHint(tableHint.trim());
        }

        JsonNode dbIdNode = body.path("databaseId");
        if (dbIdNode.isNumber()) {
            synonym.setDatabaseId(dbIdNode.asLong());
        }

        synonymRepository.save(synonym);
        semanticRecallService.refreshSynonymCache();
        return ResponseEntity.ok(synonym);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        synonymRepository.deleteById(id);
        semanticRecallService.refreshSynonymCache();
        return ResponseEntity.noContent().build();
    }
}
