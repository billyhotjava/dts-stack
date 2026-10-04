package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ForwardUndoPlan;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.UndoTargetItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Freezes a downstream closure from server ledger facts and orders it for forward undo. */
@Component
public class ModelSpecImportForwardUndoPlanner {

    public ForwardUndoPlan plan(
        List<String> requestedUniqueIds,
        List<UndoTargetItem> targetItems,
        Map<String, RevisionPins> expectedCurrentRevisions
    ) {
        if (targetItems == null || targetItems.isEmpty()) {
            throw new IllegalArgumentException("Target attempt has no handled items");
        }
        LinkedHashMap<String, UndoTargetItem> byId = new LinkedHashMap<>();
        for (UndoTargetItem item : targetItems) {
            Objects.requireNonNull(item, "Target attempt item cannot be null");
            if (byId.putIfAbsent(item.dbtUniqueId(), item) != null) {
                throw new IllegalArgumentException("Target attempt contains duplicate item identities");
            }
        }
        List<String> selected = requestedUniqueIds == null || requestedUniqueIds.isEmpty()
            ? List.copyOf(byId.keySet())
            : requestedUniqueIds.stream().distinct().toList();
        if (selected.isEmpty() || selected.stream().anyMatch(id -> !byId.containsKey(id))) {
            throw new IllegalArgumentException("Selected item is not part of the target attempt");
        }

        Set<String> closure = new LinkedHashSet<>(selected);
        boolean changed;
        do {
            changed = false;
            for (UndoTargetItem item : targetItems) {
                if (!closure.contains(item.dbtUniqueId()) && item.dependencies().stream().anyMatch(closure::contains)) {
                    changed = closure.add(item.dbtUniqueId()) || changed;
                }
            }
        } while (changed);

        Map<String, RevisionPins> expected = expectedCurrentRevisions == null ? Map.of() : expectedCurrentRevisions;
        for (String uniqueId : closure) {
            if (!Objects.equals(expected.get(uniqueId), byId.get(uniqueId).postPins())) {
                throw new IllegalArgumentException("expectedCurrentRevisions must exactly pin the server-side undo closure");
            }
        }
        if (expected.keySet().stream().anyMatch(id -> !closure.contains(id))) {
            throw new IllegalArgumentException("expectedCurrentRevisions contains an item outside the server-side undo closure");
        }

        ArrayList<UndoTargetItem> reverse = new ArrayList<>();
        for (int index = targetItems.size() - 1; index >= 0; index--) {
            UndoTargetItem item = targetItems.get(index);
            if (closure.contains(item.dbtUniqueId())) reverse.add(item);
        }
        requireDependencyOrder(reverse);
        return new ForwardUndoPlan(selected, reverse);
    }

    private static void requireDependencyOrder(List<UndoTargetItem> reverse) {
        Set<String> seen = new HashSet<>();
        Set<String> closure = reverse.stream().map(UndoTargetItem::dbtUniqueId).collect(java.util.stream.Collectors.toSet());
        for (UndoTargetItem item : reverse) {
            for (String dependency : item.dependencies()) {
                if (closure.contains(dependency) && seen.contains(dependency)) {
                    throw new IllegalArgumentException("Target attempt topology cannot be safely reversed");
                }
            }
            seen.add(item.dbtUniqueId());
        }
    }
}
