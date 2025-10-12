package com.yuzhi.dts.admin.service.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Safety net: capture last persisted/updated/deleted entity within the current thread,
 * so AdminAuditService can fill details.target_table/target_id when callers forget.
 */
@Aspect
@Component
public class AuditEntityCaptureAspect {
    private static final Logger log = LoggerFactory.getLogger(AuditEntityCaptureAspect.class);

    // Capture CrudRepository.save(..) and saveAll(..)
    @AfterReturning(
        pointcut = "execution(* org.springframework.data.repository.CrudRepository+.save(..))",
        returning = "ret"
    )
    public void afterRepositorySave(Object ret) {
        capture("UPSERT", ret);
    }

    @AfterReturning(
        pointcut = "execution(* org.springframework.data.repository.CrudRepository+.saveAll(..))",
        returning = "ret"
    )
    public void afterRepositorySaveAll(Object ret) {
        if (ret instanceof Iterable<?> it) {
            for (Object o : it) capture("UPSERT", o);
        }
    }

    // Capture CrudRepository.delete*(..)
    @AfterReturning("execution(* org.springframework.data.repository.CrudRepository+.delete*(..)) && args(arg,..)")
    public void afterRepositoryDeleteArg(Object arg) {
        if (arg != null) capture("DELETE", arg);
    }

    // Capture EntityManager persist/merge/remove as a fallback
    @AfterReturning("execution(* jakarta.persistence.EntityManager.persist(..)) && args(entity)")
    public void afterEmPersist(Object entity) { capture("INSERT", entity); }

    @AfterReturning("execution(* jakarta.persistence.EntityManager.merge(..)) && args(entity)")
    public void afterEmMerge(Object entity) { capture("UPDATE", entity); }

    @AfterReturning("execution(* jakarta.persistence.EntityManager.remove(..)) && args(entity)")
    public void afterEmRemove(Object entity) { capture("DELETE", entity); }

    private void capture(String op, Object entity) {
        if (entity == null) return;
        // unwrap collections passed to deleteAll or similar
        if (entity instanceof Collection<?> col) {
            for (Object o : col) capture(op, o);
            return;
        }
        Class<?> type = entity.getClass();
        // Only consider JPA entities
        if (type.getAnnotation(Entity.class) == null) return;
        String table = resolveTableName(type);
        String id = resolveId(entity, type);
        if (table != null && id != null) {
            AuditEntityContext.set(table, id, op);
            if (log.isDebugEnabled()) log.debug("Captured entity op={} table={} id={}", op, table, id);
        }
    }

    private String resolveTableName(Class<?> type) {
        Table t = type.getAnnotation(Table.class);
        if (t != null && t.name() != null && !t.name().isBlank()) {
            return t.name();
        }
        Entity e = type.getAnnotation(Entity.class);
        String name = e != null ? e.name() : null;
        if (name != null && !name.isBlank()) return toSnake(name);
        return toSnake(type.getSimpleName());
    }

    private String toSnake(String in) {
        String s = in.replaceAll("[^A-Za-z0-9]+", "_");
        s = s.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        s = s.toLowerCase();
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        return s;
    }

    private String resolveId(Object entity, Class<?> type) {
        // Try getId()
        try {
            Method m = type.getMethod("getId");
            Object v = m.invoke(entity);
            if (v != null) return String.valueOf(v);
        } catch (Exception ignored) {}
        // Try field annotated with @Id
        for (Field f : type.getDeclaredFields()) {
            if (f.getAnnotation(Id.class) != null) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(entity);
                    if (v != null) return String.valueOf(v);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}

