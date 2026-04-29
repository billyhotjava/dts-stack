package com.yuzhi.dts.admin.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.security.session.AdminSessionRegistry.ValidationResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class AdminSessionRegistryTest {

    @Test
    void secondLoginRevokesPreviousSessionAsConcurrent() {
        AdminSessionRepository repository = InMemoryAdminSessionRepository.create();
        AdminSessionRegistry registry = new AdminSessionRegistry(10, repository);
        Instant tokenExpiry = Instant.now().plusSeconds(600);

        AdminSessionRegistry.SessionRegistration first = registry.registerLogin(
            "SysAdmin",
            "kc-session-1",
            "access-1",
            "refresh-1",
            tokenExpiry,
            tokenExpiry
        );

        AdminSessionRegistry.SessionRegistration second = registry.registerLogin(
            "sysadmin",
            "kc-session-2",
            "access-2",
            "refresh-2",
            tokenExpiry,
            tokenExpiry
        );

        assertThat(second.takeover()).isTrue();
        assertThat(second.terminatedSessions()).isEqualTo(1);
        assertThat(first.session().getRevokedReason()).isEqualTo(AdminSessionCloseReason.CONCURRENT);
        assertThat(first.session().getRevokedBySessionId()).isEqualTo(second.session().getSessionId());
        assertThat(registry.validate("access-1", "kc-session-1", "sysadmin")).isEqualTo(ValidationResult.CONCURRENT);
        assertThat(registry.validate("access-2", "kc-session-2", "sysadmin")).isEqualTo(ValidationResult.ACTIVE);
    }

    private static final class InMemoryAdminSessionRepository implements InvocationHandler {

        private final Map<UUID, AdminSessionEntity> storage = new ConcurrentHashMap<>();
        private final Map<String, UUID> byAccessHash = new ConcurrentHashMap<>();
        private final Map<String, UUID> byRefreshHash = new ConcurrentHashMap<>();

        static AdminSessionRepository create() {
            InMemoryAdminSessionRepository handler = new InMemoryAdminSessionRepository();
            return (AdminSessionRepository) Proxy.newProxyInstance(
                AdminSessionRepository.class.getClassLoader(),
                new Class[] { AdminSessionRepository.class },
                handler
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            return switch (name) {
                case "save", "saveAndFlush" -> save((AdminSessionEntity) args[0]);
                case "findByAccessTokenHash" -> findByAccessTokenHash((String) args[0]);
                case "findByRefreshTokenHash" -> findByRefreshTokenHash((String) args[0]);
                case "findActiveSessionsForUpdate" -> findActiveSessionsForUpdate((String) args[0]);
                case "flush" -> null;
                case "findById" -> Optional.ofNullable(storage.get(args[0]));
                case "existsById" -> storage.containsKey(args[0]);
                case "count" -> (long) storage.size();
                case "deleteById" -> {
                    deleteById((UUID) args[0]);
                    yield null;
                }
                case "delete" -> {
                    delete((AdminSessionEntity) args[0]);
                    yield null;
                }
                case "deleteAll" -> {
                    storage.clear();
                    byAccessHash.clear();
                    byRefreshHash.clear();
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "InMemoryAdminSessionRepository";
                default -> throw new UnsupportedOperationException("Method not supported in test repository: " + name);
            };
        }

        private AdminSessionEntity save(AdminSessionEntity entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            AdminSessionEntity previous = storage.put(entity.getId(), entity);
            if (previous != null) {
                if (previous.getAccessTokenHash() != null && !previous.getAccessTokenHash().equals(entity.getAccessTokenHash())) {
                    byAccessHash.remove(previous.getAccessTokenHash());
                }
                if (previous.getRefreshTokenHash() != null && !previous.getRefreshTokenHash().equals(entity.getRefreshTokenHash())) {
                    byRefreshHash.remove(previous.getRefreshTokenHash());
                }
            }
            if (entity.getAccessTokenHash() != null) {
                byAccessHash.put(entity.getAccessTokenHash(), entity.getId());
            }
            if (entity.getRefreshTokenHash() != null) {
                byRefreshHash.put(entity.getRefreshTokenHash(), entity.getId());
            }
            return entity;
        }

        private Optional<AdminSessionEntity> findByAccessTokenHash(String accessHash) {
            return Optional.ofNullable(byAccessHash.get(accessHash)).map(storage::get);
        }

        private Optional<AdminSessionEntity> findByRefreshTokenHash(String refreshHash) {
            return Optional.ofNullable(byRefreshHash.get(refreshHash)).map(storage::get);
        }

        private List<AdminSessionEntity> findActiveSessionsForUpdate(String normalizedUsername) {
            return storage
                .values()
                .stream()
                .filter(entity -> normalizedUsername.equals(entity.getNormalizedUsername()) && entity.getRevokedAt() == null)
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
        }

        private void deleteById(UUID id) {
            AdminSessionEntity removed = storage.remove(id);
            if (removed != null) {
                byAccessHash.remove(removed.getAccessTokenHash());
                byRefreshHash.remove(removed.getRefreshTokenHash());
            }
        }

        private void delete(AdminSessionEntity entity) {
            if (entity != null) {
                deleteById(entity.getId());
            }
        }
    }
}
