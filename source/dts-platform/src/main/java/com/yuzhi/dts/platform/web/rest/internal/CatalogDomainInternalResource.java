package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainDictionaryReadPort;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainDictionaryReadPort.DomainRecord;
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
@RequestMapping("/api/internal/domains")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and @metricsInternalAccess.isMetricsService(authentication)")
public class CatalogDomainInternalResource {

    private static final int MAX_REFS = 200;

    private final CatalogDomainDictionaryReadPort domains;

    public CatalogDomainInternalResource(CatalogDomainDictionaryReadPort domains) {
        this.domains = domains;
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
        Map<String, List<DomainRecord>> domainsByCandidate = indexDomains(domains.findByCodeCandidates(lookupCodes));

        List<DomainContract> resolvedDomains = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (String ref : requestedRefs) {
            DomainResolution resolved = findDomain(ref, domainsByCandidate);
            if (resolved.ambiguous()) {
                ambiguous.add(ref);
                continue;
            }
            DomainRecord domain = resolved.domain();
            if (domain == null) {
                missing.add(ref);
                continue;
            }
            resolvedDomains.add(new DomainContract(ref, domain.id(), domain.code(), domain.name(), domain.owner()));
        }
        return ResponseEntity.ok(new ResolveResponse(resolvedDomains, missing, ambiguous));
    }

    private static List<String> normalizeRequestedRefs(Collection<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ref : refs) {
            if (StringUtils.hasText(ref)) {
                normalized.add(ref.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    private static Map<String, List<DomainRecord>> indexDomains(Collection<DomainRecord> domains) {
        Map<String, List<DomainRecord>> index = new LinkedHashMap<>();
        if (domains == null) {
            return index;
        }
        for (DomainRecord domain : domains) {
            if (domain == null || !StringUtils.hasText(domain.code())) {
                continue;
            }
            for (String candidate : codeCandidates(domain.code())) {
                index.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(domain);
            }
        }
        return index;
    }

    private static DomainResolution findDomain(String ref, Map<String, List<DomainRecord>> domainsByCandidate) {
        for (String candidate : codeCandidates(ref)) {
            List<DomainRecord> domains = distinctDomains(domainsByCandidate.get(candidate));
            if (domains.size() == 1) {
                return new DomainResolution(domains.get(0), false);
            }
            if (domains.size() > 1) {
                return new DomainResolution(null, true);
            }
        }
        return new DomainResolution(null, false);
    }

    private static List<DomainRecord> distinctDomains(List<DomainRecord> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        Map<UUID, DomainRecord> byId = new LinkedHashMap<>();
        for (DomainRecord domain : domains) {
            if (domain == null || domain.id() == null) {
                continue;
            }
            byId.putIfAbsent(domain.id(), domain);
        }
        return List.copyOf(byId.values());
    }

    private static Set<String> codeCandidates(String ref) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (!StringUtils.hasText(ref)) {
            return candidates;
        }
        String normalized = ref.trim().toLowerCase(Locale.ROOT);
        addCandidate(candidates, normalized);
        int colon = normalized.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            String suffix = normalized.substring(colon + 1);
            addCandidate(candidates, suffix);
            addCandidate(candidates, normalized.substring(0, colon) + "." + suffix);
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            addCandidate(candidates, normalized.substring(slash + 1));
        }
        if (normalized.startsWith("domain.")) {
            addCandidate(candidates, normalized.substring("domain.".length()));
        }
        if (normalized.startsWith("catalog_domain.")) {
            addCandidate(candidates, normalized.substring("catalog_domain.".length()));
        }
        return candidates;
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (StringUtils.hasText(value)) {
            candidates.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    public record ResolveRequest(List<String> refs) {}
    private record DomainResolution(DomainRecord domain, boolean ambiguous) {}

    public record ResolveResponse(List<DomainContract> domains, List<String> missing, List<String> ambiguous) {}
    public record DomainContract(String ref, UUID id, String code, String name, String owner) {}
}
