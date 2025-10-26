package com.yuzhi.dts.admin.service.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

/**
 * Aspect to capture entity states before and after repository operations.
 * It intercepts calls to CrudRepository and stores the 'before' and 'after'
 * states of the entity in a ThreadLocal AuditEntityContext for the audit service to consume.
 */
@Aspect
@Component
public class AuditEntityCaptureAspect {
    private static final Logger log = LoggerFactory.getLogger(AuditEntityCaptureAspect.class);

    private final EntityManager entityManager;

    public AuditEntityCaptureAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.save(..)) && args(entity)")
    public Object aroundSave(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        return handleSingleSave(pjp, entity);
    }

    @Around("execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAndFlush(..)) && args(entity)")
    public Object aroundSaveAndFlush(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        return handleSingleSave(pjp, entity);
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.saveAll(..)) && args(entities)")
    public Object aroundSaveAll(ProceedingJoinPoint pjp, Iterable<?> entities) throws Throwable {
        if (entities == null) {
            return pjp.proceed();
        }
        Object lastAuditable = null;
        for (Object candidate : entities) {
            if (isAuditable(candidate)) {
                lastAuditable = candidate;
            }
        }
        if (lastAuditable == null) {
            return pjp.proceed();
        }
        OperationCapture capture = prepareCapture(lastAuditable);
        Object outcome = pjp.proceed();
        Object persisted = extractLastPersisted(outcome);
        if (persisted != null) {
            AuditEntityContext.setAfter(persisted);
            String finalId = resolveIdString(persisted);
            AuditEntityContext.setOperation(capture.operation, capture.tableName, finalId != null ? finalId : capture.initialId);
        } else {
            AuditEntityContext.setOperation(capture.operation, capture.tableName, capture.initialId);
        }
        return outcome;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.deleteById(..)) && args(id)")
    public Object aroundDeleteById(ProceedingJoinPoint pjp, Object id) throws Throwable {
        captureDeleteByIdentifier(pjp.getTarget(), id);
        return pjp.proceed();
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.deleteAllById(..)) && args(ids)")
    public Object aroundDeleteAllById(ProceedingJoinPoint pjp, Iterable<?> ids) throws Throwable {
        if (ids != null) {
            for (Object id : ids) {
                captureDeleteByIdentifier(pjp.getTarget(), id);
            }
        }
        return pjp.proceed();
    }

    @Before("execution(* org.springframework.data.repository.CrudRepository+.delete*(..)) && args(entity)")
    public void beforeDelete(Object entity) {
        if (entity instanceof Collection<?> col) {
            col.forEach(this::captureDelete);
        } else {
            captureDelete(entity);
        }
    }

    private Object handleSingleSave(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        if (!isAuditable(entity)) {
            return pjp.proceed();
        }
        OperationCapture capture = prepareCapture(entity);
        Object after = pjp.proceed();
        if (after != null && isAuditable(after)) {
            AuditEntityContext.setAfter(after);
            String finalId = resolveIdString(after);
            AuditEntityContext.setOperation(capture.operation, capture.tableName, finalId != null ? finalId : capture.initialId);
        } else {
            AuditEntityContext.setOperation(capture.operation, capture.tableName, capture.initialId);
        }
        return after;
    }

    private OperationCapture prepareCapture(Object entity) {
        Class<?> entityClass = entity.getClass();
        String tableName = sanitizeTableName(resolveTableName(entityClass));
        Object id = getEntityId(entity);
        String operation = id == null ? "INSERT" : "UPDATE";
        String initialId = id == null ? null : String.valueOf(id);
        if (id != null) {
            Object before = findEntityById(entityClass, id);
            if (before != null) {
                entityManager.detach(before);
                AuditEntityContext.setBefore(before);
            }
        }
        AuditEntityContext.setOperation(operation, tableName, initialId);
        return new OperationCapture(operation, tableName, initialId);
    }

    private Object extractLastPersisted(Object outcome) {
        if (outcome instanceof Iterable<?> iterable) {
            Object last = null;
            for (Object obj : iterable) {
                if (isAuditable(obj)) {
                    last = obj;
                }
            }
            return last;
        }
        return isAuditable(outcome) ? outcome : null;
    }

    private void captureDelete(Object entity) {
        if (!isAuditable(entity)) {
            return;
        }
        AuditEntityContext.setBefore(entity);
        String id = resolveIdString(entity);
        String tableName = sanitizeTableName(resolveTableName(entity.getClass()));
        AuditEntityContext.setOperation("DELETE", tableName, id);
        if (log.isDebugEnabled()) {
            log.debug("Captured entity for DELETE: table={}, id={}", tableName, id);
        }
    }

    private void captureDeleteByIdentifier(Object repository, Object id) {
        if (id == null) {
            return;
        }
        Class<?> domainType = resolveDomainType(repository);
        if (domainType == null) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to resolve domain type for repository {}", repository);
            }
            return;
        }
        Object entity = findEntityById(domainType, id);
        if (entity != null) {
            captureDelete(entity);
        } else {
            String tableName = sanitizeTableName(resolveTableName(domainType));
            AuditEntityContext.setOperation("DELETE", tableName, String.valueOf(id));
        }
    }

    private boolean isAuditable(Object entity) {
        return entity != null && entity.getClass().getAnnotation(Entity.class) != null;
    }

    private Object findEntityById(Class<?> entityClass, Object id) {
        try {
            return entityManager.find(entityClass, id);
        } catch (Exception e) {
            log.warn("Could not find entity of type {} with id {} for audit capture: {}", entityClass.getSimpleName(), id, e.getMessage());
            return null;
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

    private Object getEntityId(Object entity) {
        if (entity == null) return null;
        // Try getId() method first
        try {
            Method m = entity.getClass().getMethod("getId");
            return m.invoke(entity);
        } catch (Exception ignored) {}
        // Fallback to field annotated with @Id
        for (Field f : entity.getClass().getDeclaredFields()) {
            if (f.getAnnotation(Id.class) != null) {
                try {
                    f.setAccessible(true);
                    return f.get(entity);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String resolveIdString(Object entity) {
        Object id = getEntityId(entity);
        return id != null ? String.valueOf(id) : null;
    }

    private Class<?> resolveDomainType(Object repository) {
        if (repository == null) {
            return null;
        }
        Class<?> targetClass = AopUtils.getTargetClass(repository);
        if (targetClass == null) {
            targetClass = repository.getClass();
        }
        Class<?>[] resolved = GenericTypeResolver.resolveTypeArguments(targetClass, org.springframework.data.repository.CrudRepository.class);
        if (resolved != null && resolved.length > 0) {
            return resolved[0];
        }
        for (Class<?> iface : targetClass.getInterfaces()) {
            resolved = GenericTypeResolver.resolveTypeArguments(iface, org.springframework.data.repository.CrudRepository.class);
            if (resolved != null && resolved.length > 0) {
                return resolved[0];
            }
        }
        if (repository instanceof Advised advised) {
            try {
                Object target = advised.getTargetSource().getTarget();
                if (target != null && target != repository) {
                    return resolveDomainType(target);
                }
            } catch (Exception ex) {
                log.debug("Failed to resolve advised target for repository {}: {}", repository, ex.toString());
            }
        }
        return null;
    }

    private String sanitizeTableName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        return s;
    }

    private static final class OperationCapture {
        final String operation;
        final String tableName;
        final String initialId;
        OperationCapture(String operation, String tableName, String initialId) {
            this.operation = operation;
            this.tableName = tableName;
            this.initialId = initialId;
        }
    }
}
