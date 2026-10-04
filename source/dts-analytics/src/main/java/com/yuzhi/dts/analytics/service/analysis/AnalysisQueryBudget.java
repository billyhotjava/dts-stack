package com.yuzhi.dts.analytics.service.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalysisQueryBudget {

    private final int userLimit;
    private final int departmentLimit;
    private final int globalLimit;
    private final Map<String, Integer> activeByUser = new HashMap<>();
    private final Map<String, Integer> activeByDepartment = new HashMap<>();
    private int activeGlobal;

    public AnalysisQueryBudget(
        @Value("${analytics.query-budget.user-concurrency:3}") int userLimit,
        @Value("${analytics.query-budget.department-concurrency:20}") int departmentLimit,
        @Value("${analytics.query-budget.global-concurrency:100}") int globalLimit
    ) {
        if (userLimit < 1 || departmentLimit < 1 || globalLimit < 1) {
            throw new IllegalArgumentException("analysis query concurrency limits must be positive");
        }
        this.userLimit = userLimit;
        this.departmentLimit = departmentLimit;
        this.globalLimit = globalLimit;
    }

    public synchronized Lease acquire(String userKey, String departmentKey) {
        String user = normalize(userKey, "unknown-user");
        String department = normalize(departmentKey, "unknown-department");
        if (activeByUser.getOrDefault(user, 0) >= userLimit) {
            throw new AnalysisRateLimitException("user", 1);
        }
        if (activeByDepartment.getOrDefault(department, 0) >= departmentLimit) {
            throw new AnalysisRateLimitException("department", 1);
        }
        if (activeGlobal >= globalLimit) {
            throw new AnalysisRateLimitException("global", 1);
        }
        activeByUser.merge(user, 1, Integer::sum);
        activeByDepartment.merge(department, 1, Integer::sum);
        activeGlobal++;
        return new Lease(this, user, department);
    }

    public synchronized int activeForUser(String userKey) {
        return activeByUser.getOrDefault(normalize(userKey, "unknown-user"), 0);
    }

    public synchronized int activeGlobal() {
        return activeGlobal;
    }

    private synchronized void release(String user, String department) {
        decrement(activeByUser, user);
        decrement(activeByDepartment, department);
        activeGlobal = Math.max(0, activeGlobal - 1);
    }

    private void decrement(Map<String, Integer> counts, String key) {
        int next = counts.getOrDefault(key, 0) - 1;
        if (next <= 0) counts.remove(key);
        else counts.put(key, next);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    public static final class Lease implements AutoCloseable {

        private final AnalysisQueryBudget owner;
        private final String user;
        private final String department;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Lease(AnalysisQueryBudget owner, String user, String department) {
            this.owner = owner;
            this.user = user;
            this.department = department;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) owner.release(user, department);
        }
    }
}
