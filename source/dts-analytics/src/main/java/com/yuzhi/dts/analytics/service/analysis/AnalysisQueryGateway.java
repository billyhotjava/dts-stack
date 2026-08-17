package com.yuzhi.dts.analytics.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.QueryCacheService;
import com.yuzhi.dts.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService.AnalyticsAuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class AnalysisQueryGateway {

    private final GovernedAnalysisDatasetContractProvider contractProvider;
    private final AnalyticsDatabaseBindingResolver databaseBindingResolver;
    private final AnalysisSqlCompiler compiler;
    private final AnalysisPolicyPlanner policyPlanner;
    private final QueryExecutionFacade executionFacade;
    private final QueryCacheService cacheService;
    private final AnalysisQueryBudget queryBudget;
    private final AnalyticsAuditForwarderService auditService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ActiveQuery> activeQueries = new ConcurrentHashMap<>();

    public AnalysisQueryGateway(
        GovernedAnalysisDatasetContractProvider contractProvider,
        AnalyticsDatabaseBindingResolver databaseBindingResolver,
        AnalysisSqlCompiler compiler,
        AnalysisPolicyPlanner policyPlanner,
        QueryExecutionFacade executionFacade,
        QueryCacheService cacheService,
        AnalysisQueryBudget queryBudget,
        AnalyticsAuditForwarderService auditService,
        ObjectMapper objectMapper
    ) {
        this.contractProvider = contractProvider;
        this.databaseBindingResolver = databaseBindingResolver;
        this.compiler = compiler;
        this.policyPlanner = policyPlanner;
        this.executionFacade = executionFacade;
        this.cacheService = cacheService;
        this.queryBudget = queryBudget;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public AnalysisQueryResult preview(
        AnalyticsUser actor,
        AnalysisQuerySpec querySpec,
        AnalysisRequestContext requestContext
    ) {
        requireActor(actor);
        if (querySpec == null || querySpec.dataset() == null) {
            throw new AnalysisSpecValidationException("ANALYSIS_DATASET_REQUIRED", "dataset", "dataset reference is required");
        }
        AnalysisQuerySpec.DatasetRef dataset = querySpec.dataset();
        GovernedAnalysisDatasetContract contract = contractProvider.get(dataset.id(), dataset.version(), dataset.checksum());
        AnalysisPolicyPlanner.PolicyPlan policy = policyPlanner.plan(actor, requestContext, contract);
        long databaseId = databaseBindingResolver.requireDatabaseId(contract.sourceDatasourceId());
        AnalysisSqlCompiler.CompiledAnalysisQuery compiled = compiler.compile(
            querySpec,
            contract,
            databaseId,
            policy.rowPredicates()
        );
        ObjectNode datasetQuery = objectMapper.createObjectNode();
        datasetQuery.put("database", compiled.databaseId());
        datasetQuery.put("type", "native");
        datasetQuery.putObject("native").put("query", compiled.sql());
        QueryExecutionFacade.PreparedQuery validated = executionFacade.prepare(
            datasetQuery,
            null,
            null,
            compiled.constraints()
        );
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
            validated.databaseId(),
            validated.type(),
            validated.sql(),
            compiled.bindings(),
            null,
            validated.constraints()
        );
        GatewayExecution execution = executePrepared(
            actor,
            prepared,
            requestContext,
            contract.contractChecksum(),
            compiled.requestedLimit(),
            policy.policyContextHash(),
            false
        );
        return execution.result();
    }

    public GatewayExecution executePrepared(
        AnalyticsUser actor,
        QueryExecutionFacade.PreparedQuery prepared,
        AnalysisRequestContext requestContext,
        String contractChecksum,
        int requestedLimit
    ) {
        requireActor(actor);
        return executePrepared(
            actor,
            prepared,
            requestContext,
            contractChecksum,
            requestedLimit,
            policyPlanner.legacyPolicyHash(actor, requestContext),
            false
        );
    }

    public GatewayExecution executePrepared(
        AnalyticsUser actor,
        QueryExecutionFacade.PreparedQuery prepared,
        AnalysisRequestContext requestContext,
        String contractChecksum,
        int requestedLimit,
        boolean skipCache
    ) {
        requireActor(actor);
        return executePrepared(
            actor,
            prepared,
            requestContext,
            contractChecksum,
            requestedLimit,
            policyPlanner.legacyPolicyHash(actor, requestContext),
            skipCache
        );
    }

    private GatewayExecution executePrepared(
        AnalyticsUser actor,
        QueryExecutionFacade.PreparedQuery prepared,
        AnalysisRequestContext requestContext,
        String contractChecksum,
        int requestedLimit,
        String policyContextHash,
        boolean skipCache
    ) {
        if (prepared == null) {
            throw new AnalysisSpecValidationException("ANALYSIS_QUERY_PLAN_REQUIRED", "query", "prepared query is required");
        }
        int safeLimit = Math.max(1, Math.min(AnalysisQuerySpecValidator.MAX_LIMIT, requestedLimit));
        String queryId = queryId(contextCorrelation(requestContext));
        long started = System.nanoTime();
        AnalysisRequestContext context = requestContext == null
            ? new AnalysisRequestContext(null, null, null, null, null, null)
            : requestContext;
        ObjectNode cacheKey = cacheKey(prepared, contractChecksum, policyContextHash);
        ActiveQuery activeQuery = new ActiveQuery(actor.getId(), Thread.currentThread(), new AtomicBoolean(false));
        if (activeQueries.putIfAbsent(queryId, activeQuery) != null) {
            throw new AnalysisConflictException("ANALYSIS_QUERY_ID_CONFLICT", "query id is already active");
        }
        try (AnalysisQueryBudget.Lease ignored = queryBudget.acquire(String.valueOf(actor.getId()), context.department())) {
            Optional<DatasetQueryService.DatasetResult> cached = skipCache
                ? Optional.empty()
                : cacheService.get(prepared.databaseId(), cacheKey, actor.getId());
            DatasetQueryService.DatasetResult datasetResult;
            boolean cacheHit;
            if (cached.isPresent()) {
                datasetResult = cached.get();
                cacheHit = true;
            } else {
                datasetResult = executionFacade.executeWithCompliance(prepared);
                if (!skipCache) cacheService.put(prepared.databaseId(), cacheKey, actor.getId(), datasetResult);
                cacheHit = false;
            }
            List<List<Object>> sourceRows = datasetResult.rows() == null ? List.of() : datasetResult.rows();
            boolean truncated = sourceRows.size() > safeLimit;
            List<List<Object>> rows = truncated ? List.copyOf(sourceRows.subList(0, safeLimit)) : List.copyOf(sourceRows);
            DatasetQueryService.DatasetResult bounded = new DatasetQueryService.DatasetResult(
                rows,
                datasetResult.cols(),
                datasetResult.resultsMetadataColumns(),
                datasetResult.resultsTimezone()
            );
            AnalysisQueryResult result = new AnalysisQueryResult(
                queryId,
                datasetResult.cols() == null ? List.of() : datasetResult.cols(),
                rows,
                rows.size(),
                truncated,
                cacheHit,
                elapsedMillis(started),
                contractChecksum
            );
            audit(actor, context, queryId, "SUCCESS", result.durationMs());
            return new GatewayExecution(result, bounded);
        } catch (AnalysisRateLimitException failure) {
            audit(actor, context, queryId, "THROTTLED", elapsedMillis(started));
            throw failure;
        } catch (SQLException failure) {
            if (activeQuery.cancelled().get()) {
                audit(actor, context, queryId, "CANCELLED", elapsedMillis(started));
                throw new AnalysisQueryCancelledException();
            }
            audit(actor, context, queryId, isTimeout(failure) ? "TIMEOUT" : "FAIL", elapsedMillis(started));
            if (isTimeout(failure)) {
                throw new AnalysisQueryTimeoutException("query exceeded the 30 second execution budget", failure);
            }
            throw new AnalysisDependencyException("ANALYSIS_DATASOURCE_UNAVAILABLE", "query datasource is unavailable", failure);
        } catch (RuntimeException failure) {
            audit(actor, context, queryId, "FAIL", elapsedMillis(started));
            throw failure;
        } finally {
            activeQueries.remove(queryId, activeQuery);
            if (activeQuery.cancelled().get()) Thread.interrupted();
        }
    }

    public boolean cancelQuery(String queryId, AnalyticsUser actor) {
        requireActor(actor);
        if (queryId == null || queryId.isBlank()) return false;
        ActiveQuery active = activeQueries.get(queryId.trim());
        if (active == null) return false;
        if (!actor.isSuperuser() && !actor.getId().equals(active.actorId())) {
            throw new AnalysisForbiddenException("active query belongs to another actor");
        }
        active.cancelled().set(true);
        active.thread().interrupt();
        return true;
    }

    private ObjectNode cacheKey(
        QueryExecutionFacade.PreparedQuery prepared,
        String contractChecksum,
        String policyContextHash
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "analysis-gateway");
        node.put("contractChecksum", text(contractChecksum));
        node.put("policyContextHash", text(policyContextHash));
        node.put("queryHash", sha256(prepared.sql() + "|" + safeBindings(prepared.bindings())));
        return node;
    }

    private String safeBindings(List<Object> bindings) {
        try {
            JsonNode node = objectMapper.valueToTree(bindings == null ? List.of() : bindings);
            return node.toString();
        } catch (RuntimeException failure) {
            return "bindings-unavailable";
        }
    }

    private void requireActor(AnalyticsUser actor) {
        if (actor == null || actor.getId() == null || !actor.isActive()) {
            throw new AnalysisForbiddenException("authenticated active actor is required");
        }
    }

    private boolean isTimeout(SQLException failure) {
        String state = failure.getSQLState();
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(java.util.Locale.ROOT);
        return "57014".equals(state) || message.contains("timeout") || message.contains("timed out");
    }

    private void audit(
        AnalyticsUser actor,
        AnalysisRequestContext context,
        String queryId,
        String result,
        long durationMs
    ) {
        String actorKey = actor.getPlatformUsername();
        if (actorKey == null || actorKey.isBlank()) actorKey = actor.getEmail();
        if (actorKey == null || actorKey.isBlank()) actorKey = String.valueOf(actor.getId());
        auditService.record(new AnalyticsAuditEvent(
            actorKey,
            actor.getEmail(),
            "governed-bi",
            "ANALYSIS_QUERY",
            "执行治理分析查询",
            "QUERY",
            "ANALYSIS_QUERY",
            queryId,
            result,
            "POST",
            context.requestUri(),
            context.clientIp(),
            null,
            Math.toIntExact(Math.min(Integer.MAX_VALUE, durationMs))
        ));
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String contextCorrelation(AnalysisRequestContext context) {
        return context == null ? null : context.correlationId();
    }

    private String queryId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9_-]{1,128}")) return candidate;
        return UUID.randomUUID().toString();
    }

    private record ActiveQuery(Long actorId, Thread thread, AtomicBoolean cancelled) {}

    public record AnalysisQueryResult(
        String queryId,
        List<Map<String, Object>> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated,
        boolean cacheHit,
        long durationMs,
        String contractChecksum
    ) {}

    public record GatewayExecution(
        AnalysisQueryResult result,
        DatasetQueryService.DatasetResult datasetResult
    ) {}
}
