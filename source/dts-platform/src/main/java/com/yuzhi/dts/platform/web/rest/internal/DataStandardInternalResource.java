package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
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
@RequestMapping("/api/internal/data-standards")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and @metricsInternalAccess.isMetricsService(authentication)")
public class DataStandardInternalResource {

    private static final int MAX_REFS = 200;

    private final DataStandardRepository standardRepository;

    public DataStandardInternalResource(DataStandardRepository standardRepository) {
        this.standardRepository = standardRepository;
    }

    @PostMapping("/resolve")
    public ResponseEntity<ResolveResponse> resolve(@RequestBody(required = false) ResolveRequest request) {
        List<String> requestedRefs = normalizeRequestedRefs(request != null ? request.refs() : null);
        if (requestedRefs.isEmpty()) {
            return ResponseEntity.ok(new ResolveResponse(List.of(), List.of(), List.of(), List.of()));
        }
        if (requestedRefs.size() > MAX_REFS) {
            return ResponseEntity.badRequest().body(new ResolveResponse(List.of(), requestedRefs, List.of(), List.of()));
        }

        Set<String> lookupCodes = new LinkedHashSet<>();
        requestedRefs.forEach(ref -> lookupCodes.addAll(codeCandidates(ref)));
        Map<String, List<DataStandard>> standardsByCandidate = indexStandards(standardRepository.findByCodeLowerIn(lookupCodes));

        List<StandardContract> standards = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (String ref : requestedRefs) {
            StandardResolution resolved = findStandard(ref, standardsByCandidate);
            if (resolved.ambiguous()) {
                ambiguous.add(ref);
                continue;
            }
            DataStandard standard = resolved.standard();
            if (standard == null) {
                missing.add(ref);
                continue;
            }
            boolean active = DataStandardStatus.ACTIVE.equals(standard.getStatus());
            standards.add(
                new StandardContract(
                    ref,
                    standard.getId(),
                    standard.getCode(),
                    standard.getName(),
                    standard.getDomain(),
                    standard.getDataType(),
                    standard.getNullable(),
                    standard.getStatus() != null ? standard.getStatus().name() : null,
                    active
                )
            );
            if (!active) {
                inactive.add(ref);
            }
        }
        return ResponseEntity.ok(new ResolveResponse(standards, missing, inactive, ambiguous));
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

    private static Map<String, List<DataStandard>> indexStandards(Collection<DataStandard> standards) {
        Map<String, List<DataStandard>> index = new LinkedHashMap<>();
        if (standards == null) {
            return index;
        }
        for (DataStandard standard : standards) {
            if (standard == null || !StringUtils.hasText(standard.getCode())) {
                continue;
            }
            for (String candidate : codeCandidates(standard.getCode())) {
                index.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(standard);
            }
        }
        return index;
    }

    private static StandardResolution findStandard(String ref, Map<String, List<DataStandard>> standardsByCandidate) {
        for (String candidate : codeCandidates(ref)) {
            List<DataStandard> standards = distinctStandards(standardsByCandidate.get(candidate));
            if (standards.size() == 1) {
                return new StandardResolution(standards.get(0), false);
            }
            if (standards.size() > 1) {
                return new StandardResolution(null, true);
            }
        }
        return new StandardResolution(null, false);
    }

    private static List<DataStandard> distinctStandards(List<DataStandard> standards) {
        if (standards == null || standards.isEmpty()) {
            return List.of();
        }
        Map<UUID, DataStandard> byId = new LinkedHashMap<>();
        for (DataStandard standard : standards) {
            if (standard == null || standard.getId() == null) {
                continue;
            }
            byId.putIfAbsent(standard.getId(), standard);
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
        if (normalized.startsWith("data_standard.")) {
            addCandidate(candidates, normalized.substring("data_standard.".length()));
        }
        if (normalized.startsWith("standard.")) {
            addCandidate(candidates, normalized.substring("standard.".length()));
        }
        return candidates;
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (StringUtils.hasText(value)) {
            candidates.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    public record ResolveRequest(List<String> refs) {}
    private record StandardResolution(DataStandard standard, boolean ambiguous) {}

    public record ResolveResponse(List<StandardContract> standards, List<String> missing, List<String> inactive, List<String> ambiguous) {}

    public record StandardContract(
        String ref,
        UUID id,
        String code,
        String name,
        String domain,
        String dataType,
        Boolean nullable,
        String status,
        boolean active
    ) {}
}
