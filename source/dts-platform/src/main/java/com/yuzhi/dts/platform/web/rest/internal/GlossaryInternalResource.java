package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/glossary/terms")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-metrics'")
public class GlossaryInternalResource {

    private static final int MAX_REFS = 200;

    private final ModelingGlossaryTermRepository glossaryTermRepository;

    public GlossaryInternalResource(ModelingGlossaryTermRepository glossaryTermRepository) {
        this.glossaryTermRepository = glossaryTermRepository;
    }

    @PostMapping("/resolve")
    public ResponseEntity<ResolveResponse> resolve(@RequestBody(required = false) ResolveRequest request) {
        List<String> requestedRefs = normalizeRequestedRefs(request != null ? request.refs() : null);
        if (requestedRefs.isEmpty()) {
            return ResponseEntity.ok(new ResolveResponse(List.of(), List.of(), List.of()));
        }
        if (requestedRefs.size() > MAX_REFS) {
            return ResponseEntity.badRequest().body(new ResolveResponse(List.of(), requestedRefs, List.of()));
        }

        Set<String> lookupCodes = new LinkedHashSet<>();
        requestedRefs.forEach(ref -> lookupCodes.addAll(codeCandidates(ref)));
        Map<String, ModelingGlossaryTerm> termsByCandidate = indexTerms(glossaryTermRepository.findByCodeLowerIn(lookupCodes));

        List<TermContract> terms = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        for (String ref : requestedRefs) {
            ModelingGlossaryTerm term = findTerm(ref, termsByCandidate);
            if (term == null) {
                missing.add(ref);
                continue;
            }
            boolean active = isActive(term);
            terms.add(new TermContract(ref, term.getId(), term.getCode(), term.getName(), term.getStatus(), active));
            if (!active) {
                inactive.add(ref);
            }
        }
        return ResponseEntity.ok(new ResolveResponse(terms, missing, inactive));
    }

    private static List<String> normalizeRequestedRefs(Collection<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ref : refs) {
            String text = StringUtils.trimWhitespace(ref);
            if (StringUtils.hasText(text)) {
                normalized.add(text);
            }
        }
        return List.copyOf(normalized);
    }

    private static Map<String, ModelingGlossaryTerm> indexTerms(Collection<ModelingGlossaryTerm> terms) {
        Map<String, ModelingGlossaryTerm> index = new LinkedHashMap<>();
        if (terms == null) {
            return index;
        }
        for (ModelingGlossaryTerm term : terms) {
            if (term == null || !StringUtils.hasText(term.getCode())) {
                continue;
            }
            for (String candidate : codeCandidates(term.getCode())) {
                index.putIfAbsent(candidate, term);
            }
        }
        return index;
    }

    private static ModelingGlossaryTerm findTerm(String ref, Map<String, ModelingGlossaryTerm> termsByCandidate) {
        for (String candidate : codeCandidates(ref)) {
            ModelingGlossaryTerm term = termsByCandidate.get(candidate);
            if (term != null) {
                return term;
            }
        }
        return null;
    }

    private static Set<String> codeCandidates(String ref) {
        String value = StringUtils.trimWhitespace(ref);
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, normalized);

        int colon = normalized.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            addCandidate(candidates, normalized.substring(colon + 1));
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            addCandidate(candidates, normalized.substring(slash + 1));
        }
        if (normalized.startsWith("glossary.")) {
            addCandidate(candidates, normalized.substring("glossary.".length()));
        }
        return candidates;
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (StringUtils.hasText(value)) {
            candidates.add(value);
        }
    }

    private static boolean isActive(ModelingGlossaryTerm term) {
        return "ACTIVE".equalsIgnoreCase(StringUtils.trimWhitespace(term.getStatus()));
    }

    public record ResolveRequest(List<String> refs) {}
    public record ResolveResponse(List<TermContract> terms, List<String> missing, List<String> inactive) {}
    public record TermContract(String ref, UUID id, String code, String name, String status, boolean active) {}
}
