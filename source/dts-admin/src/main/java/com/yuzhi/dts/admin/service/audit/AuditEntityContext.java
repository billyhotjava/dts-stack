package com.yuzhi.dts.admin.service.audit;

public final class AuditEntityContext {
    public static final class CapturedEntity {
        public final String tableName;
        public final String id;
        public final String op; // INSERT/UPDATE/DELETE
        public CapturedEntity(String tableName, String id, String op) {
            this.tableName = tableName;
            this.id = id;
            this.op = op;
        }
    }

    private static final ThreadLocal<CapturedEntity> LAST_ENTITY = new ThreadLocal<>();

    private AuditEntityContext() {}

    public static void set(String tableName, String id, String op) {
        if (tableName == null || tableName.isBlank() || id == null || id.isBlank()) return;
        LAST_ENTITY.set(new CapturedEntity(tableName, id, op));
    }

    public static CapturedEntity getLast() {
        return LAST_ENTITY.get();
    }

    public static void clear() {
        LAST_ENTITY.remove();
    }
}

