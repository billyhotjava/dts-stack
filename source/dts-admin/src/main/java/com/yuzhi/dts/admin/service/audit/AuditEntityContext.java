package com.yuzhi.dts.admin.service.audit;

/**
 * A thread-local context to hold entity states captured by the AuditEntityCaptureAspect
 * for consumption by the AdminAuditService.
 */
public final class AuditEntityContext {

    /**
     * Holds the state of the audited entity.
     */
    static class Payload {
        Object before;
        Object after;
        String operation;
        String tableName;
        String entityId;
    }

    private static final ThreadLocal<Payload> CONTEXT = ThreadLocal.withInitial(Payload::new);

    private AuditEntityContext() {}

    /**
     * Sets the 'before' state of the entity for the current thread.
     * Called by the aspect before a repository update or delete operation.
     * @param entity The entity state before the operation.
     */
    public static void setBefore(Object entity) {
        if (entity == null) return;
        CONTEXT.get().before = entity;
    }

    /**
     * Sets the 'after' state of the entity for the current thread.
     * Called by the aspect after a repository create or update operation.
     * @param entity The entity state after the operation.
     */
    public static void setAfter(Object entity) {
        if (entity == null) return;
        CONTEXT.get().after = entity;
    }

    /**
     * Sets metadata about the operation being performed.
     * @param op The operation type (e.g., "INSERT", "UPDATE", "DELETE").
     * @param tableName The name of the database table.
     * @param entityId The primary key of the entity.
     */
    public static void setOperation(String op, String tableName, String entityId) {
        Payload payload = CONTEXT.get();
        payload.operation = op;
        payload.tableName = tableName;
        payload.entityId = entityId;
    }

    /**
     * Retrieves the full context payload for the current thread.
     * @return The payload containing before/after states and operation details.
     */
    public static Payload get() {
        return CONTEXT.get();
    }

    /**
     * Clears the context for the current thread to prevent memory leaks.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}

