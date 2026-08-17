package com.yuzhi.dts.analytics.service.analysis;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AnalysisPolicyPlanner {

    public PolicyPlan plan(
        AnalyticsUser actor,
        AnalysisRequestContext requestContext,
        GovernedAnalysisDatasetContract contract
    ) {
        if (actor == null || actor.getId() == null || !actor.isActive()) {
            throw new AnalysisForbiddenException("authenticated active actor is required");
        }
        AnalysisRequestContext context = requestContext == null
            ? new AnalysisRequestContext(null, null, null, null, null, null)
            : requestContext;
        int actorLevel = actor.isSuperuser() ? 3 : classificationLevel(context.classification(), false);
        int datasetLevel = classificationLevel(contract == null ? null : contract.classification(), true);
        if (actorLevel < datasetLevel) {
            throw new AnalysisForbiddenException("actor clearance is below the dataset classification");
        }

        List<AnalysisSqlCompiler.RowPredicate> predicates = new ArrayList<>();
        for (String policyRef : contract == null || contract.policyRefs() == null ? List.<String>of() : contract.policyRefs()) {
            if (policyRef == null || !policyRef.startsWith("rls:department:")) continue;
            String field = policyRef.substring("rls:department:".length()).trim();
            if (field.isEmpty() || context.department() == null || context.department().isBlank()) {
                throw new AnalysisForbiddenException("department context required by dataset row policy");
            }
            predicates.add(new AnalysisSqlCompiler.RowPredicate(field, "EQ", List.of(context.department().trim())));
        }
        String hashInput = actor.getId()
            + "|" + text(context.department())
            + "|" + text(context.classification())
            + "|" + text(context.roles())
            + "|" + String.join(",", contract == null || contract.policyRefs() == null ? List.of() : contract.policyRefs());
        return new PolicyPlan(List.copyOf(predicates), sha256(hashInput));
    }

    public String legacyPolicyHash(AnalyticsUser actor, AnalysisRequestContext requestContext) {
        AnalysisRequestContext context = requestContext == null
            ? new AnalysisRequestContext(null, null, null, null, null, null)
            : requestContext;
        String actorId = actor == null || actor.getId() == null ? "anonymous" : String.valueOf(actor.getId());
        return sha256(actorId + "|" + text(context.department()) + "|" + text(context.classification()) + "|" + text(context.roles()));
    }

    private int classificationLevel(String value, boolean contractValue) {
        String normalized = text(value).toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.startsWith("DATA_")) normalized = normalized.substring(5);
        return switch (normalized) {
            case "", "PUBLIC" -> 0;
            case "INTERNAL" -> 1;
            case "SENSITIVE", "SECRET" -> 2;
            case "CONFIDENTIAL", "TOP_SECRET", "TOPSECRET" -> 3;
            default -> {
                if (contractValue) throw new AnalysisForbiddenException("dataset classification is not recognized");
                yield 0;
            }
        };
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 32);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record PolicyPlan(List<AnalysisSqlCompiler.RowPredicate> rowPredicates, String policyContextHash) {}
}
