package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyEdge;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyGraph;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyNode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.DependencyState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-owned dependency closure and admission preview for batch release candidates.
 *
 * <p>The browser submits at most 100 roots. This service expands their immutable revision DAG,
 * reports every blocker and emits the exact candidate scope that the existing command boundary
 * persists. No root or dependency is silently removed.
 */
@Service
public class ModelReleaseCandidatePreflightService {

    public static final int MAX_DAG_NODES = 500;
    public static final int MAX_DAG_EDGES = 2_000;
    public static final int MAX_DAG_DEPTH = 20;

    private final ModelSpecApplicationService modelSpecs;

    public ModelReleaseCandidatePreflightService(ModelSpecApplicationService modelSpecs) {
        this.modelSpecs = Objects.requireNonNull(modelSpecs, "modelSpecs is required");
    }

    @Transactional(readOnly = true)
    public BatchPreflightView preview(String tenantId, CreateCandidateCommand command) {
        if (command == null) {
            throw invalid("MODEL_RELEASE_BATCH_SCOPE_INVALID", "Candidate preflight request is required");
        }
        List<ScopeEntryCommand> roots = command.entries();
        if (roots.size() > ModelReleaseCandidateContract.MAX_ROOT_ENTRIES) {
            throw invalid(
                "MODEL_RELEASE_BATCH_ROOT_LIMIT_EXCEEDED",
                "Batch materialization accepts at most 100 selected root models"
            );
        }
        if (roots.isEmpty()) {
            return new BatchPreflightView(
                command.planId(),
                command.environment(),
                true,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of()
            );
        }

        LinkedHashSet<UUID> rootIds = new LinkedHashSet<>();
        Map<UUID, ScopeEntryCommand> rootsById = new LinkedHashMap<>();
        for (ScopeEntryCommand root : roots) {
            rootIds.add(root.modelSpecId());
            rootsById.put(root.modelSpecId(), root);
        }

        Map<UUID, DependencyNode> nodes = new LinkedHashMap<>();
        Map<EdgeKey, DependencyEdge> edges = new LinkedHashMap<>();
        Map<String, PreflightBlocker> blockers = new LinkedHashMap<>();
        for (ScopeEntryCommand root : roots) {
            DependencyGraph graph = modelSpecs.dependencyGraph(tenantId, root.modelSpecId());
            mergeGraph(command.planId(), root.modelSpecId(), graph, nodes, edges, blockers);
        }

        addLimitBlocker(
            blockers,
            nodes.size() > MAX_DAG_NODES,
            "MODEL_RELEASE_DAG_NODE_LIMIT_EXCEEDED",
            "Dependency closure exceeds 500 nodes",
            Map.of("actual", nodes.size(), "maximum", MAX_DAG_NODES)
        );
        addLimitBlocker(
            blockers,
            edges.size() > MAX_DAG_EDGES,
            "MODEL_RELEASE_DAG_EDGE_LIMIT_EXCEEDED",
            "Dependency closure exceeds 2000 edges",
            Map.of("actual", edges.size(), "maximum", MAX_DAG_EDGES)
        );

        validateNodes(command.planId(), rootIds, nodes, blockers);
        validateEdges(nodes, edges.values(), blockers);
        Map<UUID, List<UUID>> upstreams = upstreams(edges.values());
        Map<UUID, Integer> rootDepths = new HashMap<>();
        detectCyclesAndDepth(rootIds, upstreams, blockers, rootDepths);
        int depth = rootDepths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        addLimitBlocker(
            blockers,
            depth > MAX_DAG_DEPTH,
            "MODEL_RELEASE_DAG_DEPTH_LIMIT_EXCEEDED",
            "Dependency closure exceeds maximum depth 20",
            Map.of("actual", depth, "maximum", MAX_DAG_DEPTH)
        );

        Set<UUID> included = includedCandidateNodes(command.planId(), rootIds, nodes);
        Map<UUID, Integer> topologyLevels = topologyLevels(included, upstreams);
        List<PreflightNode> nodeViews = nodes
            .values()
            .stream()
            .map(node ->
                new PreflightNode(
                    node.modelSpecId(),
                    node.planId(),
                    node.pinnedRevision(),
                    node.currentRevision(),
                    node.name(),
                    node.modelType(),
                    node.layer(),
                    node.status(),
                    inclusion(command.planId(), rootIds, node),
                    topologyLevels.getOrDefault(node.modelSpecId(), 0),
                    node.restricted()
                )
            )
            .sorted(
                Comparator
                    .comparingInt(PreflightNode::topologyLevel)
                    .thenComparing(PreflightNode::modelSpecId)
            )
            .toList();
        List<PreflightEdge> edgeViews = edges
            .values()
            .stream()
            .map(edge ->
                new PreflightEdge(
                    edge.fromModelSpecId(),
                    edge.toModelSpecId(),
                    edge.pinnedRevision(),
                    edge.currentRevision(),
                    edge.state()
                )
            )
            .toList();

        List<PreflightBlocker> blockerViews = List.copyOf(blockers.values());
        return new BatchPreflightView(
            command.planId(),
            command.environment(),
            blockerViews.isEmpty(),
            roots.size(),
            included.size(),
            nodes.size(),
            edges.size(),
            nodeViews,
            edgeViews,
            blockerViews
        );
    }

    @Transactional(readOnly = true)
    public PreflightResult requireEligible(String tenantId, CreateCandidateCommand command) {
        BatchPreflightView view = preview(tenantId, command);
        if (!view.eligible()) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_PREFLIGHT_BLOCKED",
                "Release candidate preflight found one or more blockers",
                Kind.UNPROCESSABLE,
                view.blockers()
            );
        }
        if (command.entries().isEmpty()) return new PreflightResult(view, command);
        Map<UUID, ScopeEntryCommand> roots = new LinkedHashMap<>();
        for (ScopeEntryCommand entry : command.entries()) roots.put(entry.modelSpecId(), entry);
        List<ScopeEntryCommand> expanded = view
            .nodes()
            .stream()
            .filter(node -> !"SATISFIED_PUBLISHED".equals(node.inclusion()))
            .filter(node -> !"BLOCKED".equals(node.inclusion()))
            .sorted(
                Comparator
                    .comparingInt(PreflightNode::topologyLevel)
                    .thenComparing(node -> roots.containsKey(node.modelSpecId()) ? 1 : 0)
                    .thenComparing(PreflightNode::modelSpecId)
            )
            .map(node -> {
                ScopeEntryCommand selected = roots.get(node.modelSpecId());
                String reason = selected == null ? "AUTO_DEPENDENCY" : selected.selectedReason();
                return new ScopeEntryCommand(node.modelSpecId(), 0, reason);
            })
            .toList();
        List<ScopeEntryCommand> ordered = new ArrayList<>(expanded.size());
        for (int index = 0; index < expanded.size(); index++) {
            ScopeEntryCommand entry = expanded.get(index);
            ordered.add(new ScopeEntryCommand(entry.modelSpecId(), index, entry.selectedReason()));
        }
        return new PreflightResult(
            view,
            new CreateCandidateCommand(
                command.planId(),
                command.environment(),
                ordered,
                command.idempotencyKey(),
                command.reason()
            )
        );
    }

    private static void mergeGraph(
        UUID planId,
        UUID requestedRootId,
        DependencyGraph graph,
        Map<UUID, DependencyNode> nodes,
        Map<EdgeKey, DependencyEdge> edges,
        Map<String, PreflightBlocker> blockers
    ) {
        if (graph == null || !requestedRootId.equals(graph.rootModelSpecId())) {
            addBlocker(
                blockers,
                "MODEL_RELEASE_DEPENDENCY_GRAPH_INVALID",
                requestedRootId,
                "Dependency graph does not match the requested root",
                Map.of("planId", planId)
            );
            return;
        }
        for (DependencyNode node : graph.nodes() == null ? List.<DependencyNode>of() : graph.nodes()) {
            DependencyNode existing = nodes.putIfAbsent(node.modelSpecId(), node);
            if (existing != null && existing.pinnedRevision() != node.pinnedRevision()) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_DEPENDENCY_REVISION_CONFLICT",
                    node.modelSpecId(),
                    "Selected roots require different revisions of the same dependency",
                    Map.of("firstRevision", existing.pinnedRevision(), "secondRevision", node.pinnedRevision())
                );
            }
        }
        for (DependencyEdge edge : graph.edges() == null ? List.<DependencyEdge>of() : graph.edges()) {
            edges.putIfAbsent(new EdgeKey(edge.fromModelSpecId(), edge.toModelSpecId(), edge.pinnedRevision()), edge);
        }
    }

    private static void validateNodes(
        UUID planId,
        Set<UUID> roots,
        Map<UUID, DependencyNode> nodes,
        Map<String, PreflightBlocker> blockers
    ) {
        for (UUID root : roots) {
            DependencyNode node = nodes.get(root);
            if (node == null || node.restricted() || !planId.equals(node.planId())) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_ROOT_SCOPE_INVALID",
                    root,
                    "A selected root is unavailable in the requested plan",
                    Map.of("planId", planId)
                );
            } else if (node.currentRevision() == null || node.currentRevision() != node.pinnedRevision()) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_ROOT_REVISION_STALE",
                    root,
                    "A selected root is not at its current revision",
                    Map.of("pinnedRevision", node.pinnedRevision())
                );
            }
        }
        for (DependencyNode node : nodes.values()) {
            if (node.restricted()) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_DEPENDENCY_SCOPE_FORBIDDEN",
                    node.modelSpecId(),
                    "A dependency is unavailable to the current actor",
                    Map.of("pinnedRevision", node.pinnedRevision())
                );
                continue;
            }
            if (!planId.equals(node.planId()) && node.status() != ModelStatus.PUBLISHED) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_CROSS_PLAN_DEPENDENCY_NOT_PUBLISHED",
                    node.modelSpecId(),
                    "Cross-plan dependencies must use a fixed published revision",
                    Map.of("pinnedRevision", node.pinnedRevision())
                );
            }
        }
    }

    private static void validateEdges(
        Map<UUID, DependencyNode> nodes,
        Iterable<DependencyEdge> edges,
        Map<String, PreflightBlocker> blockers
    ) {
        for (DependencyEdge edge : edges) {
            DependencyNode owner = nodes.get(edge.fromModelSpecId());
            DependencyNode upstream = nodes.get(edge.toModelSpecId());
            if (owner == null || upstream == null || edge.state() == DependencyState.UNKNOWN) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_DEPENDENCY_REVISION_UNKNOWN",
                    edge.toModelSpecId(),
                    "A pinned dependency revision cannot be resolved",
                    Map.of("ownerModelSpecId", edge.fromModelSpecId())
                );
                continue;
            }
            if (upstream.status() == ModelStatus.ARCHIVED) {
                addBlocker(blockers, "MODEL_RELEASE_DEPENDENCY_ARCHIVED", edge.toModelSpecId(),
                    "上游引用版本已归档，请调整引用后重试", Map.of("ownerModelSpecId", edge.fromModelSpecId(),
                    "pinnedRevision", edge.pinnedRevision(), "reason", "ARCHIVED"));
                continue;
            }
            if (edge.state() == DependencyState.STALE && upstream.status() != ModelStatus.PUBLISHED) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_DEPENDENCY_PIN_STALE",
                    edge.toModelSpecId(),
                    "An unpublished dependency pin is no longer current",
                    Map.of(
                        "pinnedRevision",
                        edge.pinnedRevision(),
                        "currentRevision",
                        edge.currentRevision() == null ? 0 : edge.currentRevision()
                    )
                );
            }
            if (
                owner.layer() != null &&
                upstream.layer() != null &&
                layerRank(upstream.layer()) > layerRank(owner.layer())
            ) {
                addBlocker(
                    blockers,
                    "MODEL_RELEASE_DEPENDENCY_LAYER_DIRECTION_INVALID",
                    edge.toModelSpecId(),
                    "A dependency points from a lower layer to a higher warehouse layer",
                    Map.of("ownerLayer", owner.layer(), "upstreamLayer", upstream.layer())
                );
            }
        }
    }

    private static Map<UUID, List<UUID>> upstreams(Iterable<DependencyEdge> edges) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        for (DependencyEdge edge : edges) {
            result.computeIfAbsent(edge.fromModelSpecId(), ignored -> new ArrayList<>()).add(edge.toModelSpecId());
        }
        return result;
    }

    private static void detectCyclesAndDepth(
        Set<UUID> roots,
        Map<UUID, List<UUID>> upstreams,
        Map<String, PreflightBlocker> blockers,
        Map<UUID, Integer> rootDepths
    ) {
        for (UUID root : roots) {
            rootDepths.put(root, depth(root, upstreams, new LinkedHashSet<>(), new HashMap<>(), blockers));
        }
    }

    private static int depth(
        UUID node,
        Map<UUID, List<UUID>> upstreams,
        LinkedHashSet<UUID> path,
        Map<UUID, Integer> memo,
        Map<String, PreflightBlocker> blockers
    ) {
        Integer cached = memo.get(node);
        if (cached != null) return cached;
        if (!path.add(node)) {
            addBlocker(
                blockers,
                "MODEL_RELEASE_DAG_CYCLE",
                node,
                "The immutable model revision graph contains a cycle",
                Map.of("path", List.copyOf(path))
            );
            return MAX_DAG_DEPTH + 1;
        }
        int result = 0;
        for (UUID upstream : upstreams.getOrDefault(node, List.of())) {
            result = Math.max(result, 1 + depth(upstream, upstreams, path, memo, blockers));
        }
        path.remove(node);
        memo.put(node, result);
        return result;
    }

    private static Set<UUID> includedCandidateNodes(
        UUID planId,
        Set<UUID> roots,
        Map<UUID, DependencyNode> nodes
    ) {
        Set<UUID> included = new HashSet<>();
        for (DependencyNode node : nodes.values()) {
            if (
                !node.restricted() &&
                (roots.contains(node.modelSpecId()) || (planId.equals(node.planId()) && node.status() != ModelStatus.PUBLISHED))
            ) {
                included.add(node.modelSpecId());
            }
        }
        return included;
    }

    private static Map<UUID, Integer> topologyLevels(
        Set<UUID> included,
        Map<UUID, List<UUID>> upstreams
    ) {
        Map<UUID, Integer> levels = new HashMap<>();
        for (UUID node : included) topologyLevel(node, included, upstreams, new HashSet<>(), levels);
        return levels;
    }

    private static int topologyLevel(
        UUID node,
        Set<UUID> included,
        Map<UUID, List<UUID>> upstreams,
        Set<UUID> path,
        Map<UUID, Integer> memo
    ) {
        Integer cached = memo.get(node);
        if (cached != null) return cached;
        if (!path.add(node)) return 0;
        int level = 0;
        for (UUID upstream : upstreams.getOrDefault(node, List.of())) {
            if (included.contains(upstream)) {
                level = Math.max(level, topologyLevel(upstream, included, upstreams, path, memo) + 1);
            }
        }
        path.remove(node);
        memo.put(node, level);
        return level;
    }

    private static String inclusion(UUID planId, Set<UUID> roots, DependencyNode node) {
        if (node.restricted()) return "BLOCKED";
        if (roots.contains(node.modelSpecId())) return "ROOT";
        if (planId.equals(node.planId()) && node.status() != ModelStatus.PUBLISHED) return "AUTO_DEPENDENCY";
        return "SATISFIED_PUBLISHED";
    }

    private static int layerRank(Layer layer) {
        return switch (layer) {
            case ODS -> 0;
            case STG -> 1;
            case DWD -> 2;
            case DWS -> 3;
            case ADS -> 4;
        };
    }

    private static void addLimitBlocker(
        Map<String, PreflightBlocker> blockers,
        boolean condition,
        String code,
        String message,
        Map<String, Object> details
    ) {
        if (condition) addBlocker(blockers, code, null, message, details);
    }

    private static void addBlocker(
        Map<String, PreflightBlocker> blockers,
        String code,
        UUID modelSpecId,
        String message,
        Map<String, Object> details
    ) {
        String key = code + ":" + modelSpecId + ":" + details;
        blockers.putIfAbsent(key, new PreflightBlocker(code, modelSpecId, message, details));
    }

    private static ModelReleaseCandidateException invalid(String code, String message) {
        return new ModelReleaseCandidateException(code, message, Kind.BAD_REQUEST, Map.of());
    }

    private record EdgeKey(UUID fromModelSpecId, UUID toModelSpecId, int pinnedRevision) {}

    public record PreflightBlocker(
        String code,
        UUID modelSpecId,
        String message,
        Map<String, Object> details
    ) {
        public PreflightBlocker {
            code = Objects.requireNonNull(code, "code is required");
            message = Objects.requireNonNull(message, "message is required");
            details = Map.copyOf(details == null ? Map.of() : details);
        }
    }

    public record PreflightNode(
        UUID modelSpecId,
        UUID planId,
        int revision,
        Integer currentRevision,
        String name,
        ModelType modelType,
        Layer layer,
        ModelStatus status,
        String inclusion,
        int topologyLevel,
        boolean restricted
    ) {}

    public record PreflightEdge(
        UUID fromModelSpecId,
        UUID toModelSpecId,
        int pinnedRevision,
        Integer currentRevision,
        DependencyState state
    ) {}

    public record BatchPreflightView(
        UUID planId,
        String environment,
        boolean eligible,
        int rootCount,
        int candidateEntryCount,
        int nodeCount,
        int edgeCount,
        List<PreflightNode> nodes,
        List<PreflightEdge> edges,
        List<PreflightBlocker> blockers
    ) {
        public BatchPreflightView {
            nodes = List.copyOf(nodes == null ? List.of() : nodes);
            edges = List.copyOf(edges == null ? List.of() : edges);
            blockers = List.copyOf(blockers == null ? List.of() : blockers);
        }
    }

    public record PreflightResult(BatchPreflightView preview, CreateCandidateCommand command) {}
}
