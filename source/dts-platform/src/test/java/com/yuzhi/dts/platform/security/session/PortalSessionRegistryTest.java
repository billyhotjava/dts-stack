package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import com.yuzhi.dts.platform.security.session.PortalSessionActivityService.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class PortalSessionRegistryTest {

    private PortalSessionRepository newRepository() {
        return InMemoryPortalSessionRepository.create();
    }

    private PortalSessionRegistry newRegistry(PortalSessionRepository repository, boolean allowTakeover) {
        return new PortalSessionRegistry(15, allowTakeover, repository);
    }

    private PortalSessionActivityService newActivityService(PortalSessionRepository repository) {
        return new PortalSessionActivityService(repository, 10L);
    }

    @Test
    void findByAccessTokenDoesNotInvalidateCurrentSession() {
        var repository = newRepository();
        var registry = newRegistry(repository, true);
        var activityService = newActivityService(repository);
        var session = registry.createSession("portaluser", List.of("ROLE_USER"), List.of("portal.view"), null);

        ValidationResult initial = activityService.touch(session.accessToken(), Instant.now().plusSeconds(1));
        assertThat(initial).isEqualTo(ValidationResult.ACTIVE);

        registry.findByAccessToken(session.accessToken()).orElseThrow();

        ValidationResult afterIntrospect = activityService.touch(session.accessToken(), Instant.now().plusSeconds(2));
        assertThat(afterIntrospect).isEqualTo(ValidationResult.ACTIVE);
    }

    @Test
    void refreshSessionRenewsCurrentSessionInPlace() {
        var repository = newRepository();
        var registry = newRegistry(repository, true);
        var activityService = newActivityService(repository);
        var session = registry.createSession("portaluser", List.of("ROLE_USER"), List.of("portal.view"), null);
        var priorToken = session.accessToken();
        var priorRefresh = session.refreshToken();
        var priorSessionId = session.sessionId();
        var priorExpiry = session.expiresAt();

        var refreshed = registry.refreshSession(priorRefresh, existing -> existing.adminTokens());

        assertThat(refreshed.sessionId()).isEqualTo(priorSessionId);
        assertThat(refreshed.accessToken()).isEqualTo(priorToken);
        assertThat(refreshed.refreshToken()).isEqualTo(priorRefresh);
        assertThat(refreshed.expiresAt()).isAfter(priorExpiry);

        var tokenState = activityService.touch(priorToken, Instant.now().plusSeconds(1));
        assertThat(tokenState).isEqualTo(ValidationResult.ACTIVE);
    }

    @Test
    void sameBrowserCreateSessionReusesExistingSessionWithoutConflict() {
        var repository = newRepository();
        var registry = newRegistry(repository, true);
        var activityService = newActivityService(repository);
        var initial = registry.createSession(
            "PortalUser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-1",
            null
        );

        assertThat(registry.hasActiveSession("portaluser", "browser-1")).isFalse();

        var reused = registry.createSession(
            "portaluser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-1",
            null
        );

        assertThat(reused.sessionId()).isEqualTo(initial.sessionId());
        assertThat(reused.accessToken()).isEqualTo(initial.accessToken());
        assertThat(reused.refreshToken()).isEqualTo(initial.refreshToken());
        assertThat(activityService.touch(initial.accessToken(), Instant.now().plusSeconds(1))).isEqualTo(ValidationResult.ACTIVE);
        assertThat(registry.hasActiveSession("PORTALUSER", "browser-2")).isTrue();
    }

    @Test
    void crossBrowserTakeoverRevokesPreviousSessionCaseInsensitively() {
        var repository = newRepository();
        var registry = newRegistry(repository, true);
        var activityService = newActivityService(repository);
        var initial = registry.createSession(
            "PortalUser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-1",
            null
        );

        var takeover = registry.createSession(
            "portaluser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-2",
            null
        );

        assertThat(activityService.touch(initial.accessToken(), Instant.now().plusSeconds(1))).isEqualTo(ValidationResult.CONCURRENT);
        assertThat(activityService.touch(takeover.accessToken(), Instant.now().plusSeconds(2))).isEqualTo(ValidationResult.ACTIVE);
        assertThat(registry.hasActiveSession("PORTALUSER", "browser-2")).isFalse();
        assertThat(registry.hasActiveSession("portaluser", "browser-3")).isTrue();
    }

    @Test
    void invalidateByRefreshTokenRevokesSession() {
        var repository = newRepository();
        var registry = newRegistry(repository, true);
        var activityService = newActivityService(repository);
        var session = registry.createSession("portaluser", List.of("ROLE_USER"), List.of("portal.view"), null);

        registry.invalidateByRefreshToken(session.refreshToken());

        var state = activityService.touch(session.accessToken(), Instant.now().plusSeconds(1));
        assertThat(state).isEqualTo(ValidationResult.EXPIRED);
    }

    @Test
    void createSessionFailsWhenTakeoverNotAllowedAcrossBrowsersOnly() {
        var repository = newRepository();
        var registry = newRegistry(repository, false);
        var initial = registry.createSession(
            "portaluser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-1",
            null
        );
        var reused = registry.createSession(
            "portaluser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-1",
            null
        );

        assertThat(reused.sessionId()).isEqualTo(initial.sessionId());
        org.assertj.core.api.Assertions
            .assertThatThrownBy(
                () ->
                    registry.createSession(
                        "portaluser",
                        List.of("ROLE_USER"),
                        List.of("portal.view"),
                        null,
                        null,
                        "Portal User",
                        "browser-2",
                        null
                    )
            )
            .isInstanceOf(PortalSessionRegistry.ActiveSessionExistsException.class);
    }

    @Test
    void createSessionFallsBackToThirtyMinuteTimeoutWhenConfiguredTimeoutIsNonPositive() {
        var repository = newRepository();
        var registry = new PortalSessionRegistry(0, true, repository);
        Instant before = Instant.now();

        var session = registry.createSession("portaluser", List.of("ROLE_USER"), List.of("portal.view"), null);
        Instant after = Instant.now();

        assertThat(session.expiresAt()).isAfterOrEqualTo(before.plusSeconds(29 * 60));
        assertThat(session.expiresAt()).isBeforeOrEqualTo(after.plusSeconds(31 * 60));
    }

    @Test
    void createSessionPreservesBrowserIdAcrossLookup() {
        var repository = newRepository();
        var registry = newRegistry(repository, true);

        var session = registry.createSession(
            "portaluser",
            List.of("ROLE_USER"),
            List.of("portal.view"),
            null,
            null,
            "Portal User",
            "browser-1",
            null
        );

        assertThat(session.browserId()).isEqualTo("browser-1");
        assertThat(registry.findByAccessToken(session.accessToken()).orElseThrow().browserId()).isEqualTo("browser-1");
    }

    private static final class InMemoryPortalSessionRepository implements InvocationHandler {

        private final Map<UUID, PortalSessionEntity> storage = new ConcurrentHashMap<>();
        private final Map<String, UUID> byAccessToken = new ConcurrentHashMap<>();
        private final Map<String, UUID> byRefreshToken = new ConcurrentHashMap<>();

        static PortalSessionRepository create() {
            InMemoryPortalSessionRepository handler = new InMemoryPortalSessionRepository();
            return (PortalSessionRepository) Proxy.newProxyInstance(
                PortalSessionRepository.class.getClassLoader(),
                new Class[] { PortalSessionRepository.class },
                handler
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            return switch (name) {
                case "save" -> save((PortalSessionEntity) args[0]);
                case "findByAccessToken" -> findByAccessToken((String) args[0]);
                case "findByRefreshToken" -> findByRefreshToken((String) args[0]);
                case "findByNormalizedUsernameAndRevokedAtIsNull" -> findActive((String) args[0]);
                case "findAllByNormalizedUsernameAndRevokedAtIsNull" -> findAllActive((String) args[0]);
                case "findAllActiveForUpdate" -> findAllActive((String) args[0]);
                case "saveAndFlush" -> save((PortalSessionEntity) args[0]);
                case "flush" -> {
                    // no-op for in-memory stub
                    yield null;
                }
                case "findById" -> Optional.ofNullable(storage.get(args[0]));
                case "existsById" -> storage.containsKey(args[0]);
                case "deleteById" -> {
                    deleteById((UUID) args[0]);
                    yield null;
                }
                case "delete" -> {
                    delete((PortalSessionEntity) args[0]);
                    yield null;
                }
                case "deleteAll" -> {
                    deleteAll();
                    yield null;
                }
                case "count" -> (long) storage.size();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "InMemoryPortalSessionRepository";
                default -> throw new UnsupportedOperationException("Method not supported in test repository: " + name);
            };
        }

        private PortalSessionEntity save(PortalSessionEntity entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            PortalSessionEntity previous = storage.put(entity.getId(), entity);
            if (previous != null) {
                if (previous.getAccessToken() != null && !previous.getAccessToken().equals(entity.getAccessToken())) {
                    byAccessToken.remove(previous.getAccessToken());
                }
                if (previous.getRefreshToken() != null && !previous.getRefreshToken().equals(entity.getRefreshToken())) {
                    byRefreshToken.remove(previous.getRefreshToken());
                }
            }
            if (entity.getAccessToken() != null) {
                byAccessToken.put(entity.getAccessToken(), entity.getId());
            }
            if (entity.getRefreshToken() != null) {
                byRefreshToken.put(entity.getRefreshToken(), entity.getId());
            }
            return entity;
        }

        private Optional<PortalSessionEntity> findByAccessToken(String token) {
            if (token == null) {
                return Optional.empty();
            }
            UUID id = byAccessToken.get(token);
            return Optional.ofNullable(id).map(storage::get);
        }

        private Optional<PortalSessionEntity> findByRefreshToken(String token) {
            if (token == null) {
                return Optional.empty();
            }
            UUID id = byRefreshToken.get(token);
            return Optional.ofNullable(id).map(storage::get);
        }

        private Optional<PortalSessionEntity> findActive(String normalizedUsername) {
            return findAllActive(normalizedUsername).stream().findFirst();
        }

        private List<PortalSessionEntity> findAllActive(String normalizedUsername) {
            if (normalizedUsername == null) {
                return List.of();
            }
            return storage
                .values()
                .stream()
                .filter(entity -> normalizedUsername.equals(entity.getNormalizedUsername()) && entity.getRevokedAt() == null)
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
        }

        private void deleteById(UUID id) {
            if (id == null) {
                return;
            }
            PortalSessionEntity removed = storage.remove(id);
            if (removed != null) {
                if (removed.getAccessToken() != null) {
                    byAccessToken.remove(removed.getAccessToken());
                }
                if (removed.getRefreshToken() != null) {
                    byRefreshToken.remove(removed.getRefreshToken());
                }
            }
        }

        private void delete(PortalSessionEntity entity) {
            if (entity != null) {
                deleteById(entity.getId());
            }
        }

        private void deleteAll() {
            storage.clear();
            byAccessToken.clear();
            byRefreshToken.clear();
        }
    }
}
