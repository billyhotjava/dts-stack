package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenEditLock;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenEditLockRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScreenEditLockService {

    private static final int DEFAULT_TTL_SECONDS = 120;
    private static final int MIN_TTL_SECONDS = 30;
    private static final int MAX_TTL_SECONDS = 600;

    private final AnalyticsScreenEditLockRepository lockRepository;

    public ScreenEditLockService(AnalyticsScreenEditLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @Transactional(readOnly = true)
    public LockSnapshot current(Long screenId, Long viewerId) {
        AnalyticsScreenEditLock lock = findActive(screenId).orElse(null);
        return toSnapshot(lock, viewerId);
    }

    public LockAcquireResult acquire(Long screenId, AnalyticsUser user, String requestId, Integer ttlSeconds, boolean forceTakeover) {
        if (screenId == null || user == null || user.getId() == null) {
            return LockAcquireResult.invalid();
        }
        int ttl = normalizeTtl(ttlSeconds);
        Instant now = Instant.now();

        Optional<AnalyticsScreenEditLock> existing = lockRepository.findByScreenId(screenId);
        if (existing.isPresent()) {
            AnalyticsScreenEditLock lock = existing.get();
            if (isExpired(lock, now)) {
                lockRepository.delete(lock);
            } else if (!user.getId().equals(lock.getOwnerId())) {
                if (!forceTakeover) {
                    return LockAcquireResult.conflict(toSnapshot(lock, user.getId()));
                }
            }
        }

        AnalyticsScreenEditLock lock = lockRepository.findByScreenId(screenId).orElseGet(AnalyticsScreenEditLock::new);
        lock.setScreenId(screenId);
        lock.setOwnerId(user.getId());
        lock.setOwnerName(resolveUserName(user));
        lock.setRequestId(trimToNull(requestId));
        if (lock.getAcquiredAt() == null || forceTakeover) {
            lock.setAcquiredAt(now);
        }
        lock.setHeartbeatAt(now);
        lock.setExpireAt(now.plusSeconds(ttl));
        lock = lockRepository.save(lock);
        return LockAcquireResult.success(toSnapshot(lock, user.getId()));
    }

    public LockAcquireResult heartbeat(Long screenId, AnalyticsUser user, String requestId, Integer ttlSeconds) {
        if (screenId == null || user == null || user.getId() == null) {
            return LockAcquireResult.invalid();
        }
        int ttl = normalizeTtl(ttlSeconds);
        Instant now = Instant.now();

        AnalyticsScreenEditLock lock = lockRepository.findByScreenId(screenId).orElse(null);
        if (lock == null || isExpired(lock, now)) {
            if (lock != null) {
                lockRepository.delete(lock);
            }
            return LockAcquireResult.notFound();
        }
        if (!user.getId().equals(lock.getOwnerId())) {
            return LockAcquireResult.conflict(toSnapshot(lock, user.getId()));
        }

        lock.setRequestId(trimToNull(requestId));
        lock.setHeartbeatAt(now);
        lock.setExpireAt(now.plusSeconds(ttl));
        lock = lockRepository.save(lock);
        return LockAcquireResult.success(toSnapshot(lock, user.getId()));
    }

    public void release(Long screenId, Long ownerId) {
        if (screenId == null || ownerId == null) {
            return;
        }
        AnalyticsScreenEditLock lock = lockRepository.findByScreenId(screenId).orElse(null);
        if (lock == null) {
            return;
        }
        if (ownerId.equals(lock.getOwnerId())) {
            lockRepository.delete(lock);
        }
    }

    @Transactional(readOnly = true)
    public boolean canMutate(Long screenId, Long actorId) {
        if (screenId == null || actorId == null) {
            return true;
        }
        AnalyticsScreenEditLock lock = lockRepository.findByScreenId(screenId).orElse(null);
        if (lock == null) {
            return true;
        }
        Instant now = Instant.now();
        if (isExpired(lock, now)) {
            return true;
        }
        return actorId.equals(lock.getOwnerId());
    }

    @Transactional(readOnly = true)
    public LockSnapshot currentBlockingLock(Long screenId, Long actorId) {
        AnalyticsScreenEditLock lock = findActive(screenId).orElse(null);
        if (lock == null) {
            return null;
        }
        if (actorId != null && actorId.equals(lock.getOwnerId())) {
            return null;
        }
        return toSnapshot(lock, actorId);
    }

    private Optional<AnalyticsScreenEditLock> findActive(Long screenId) {
        if (screenId == null) {
            return Optional.empty();
        }
        AnalyticsScreenEditLock lock = lockRepository.findByScreenId(screenId).orElse(null);
        if (lock == null) {
            return Optional.empty();
        }
        if (isExpired(lock, Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(lock);
    }

    private boolean isExpired(AnalyticsScreenEditLock lock, Instant now) {
        if (lock == null || lock.getExpireAt() == null) {
            return true;
        }
        return !lock.getExpireAt().isAfter(now);
    }

    private int normalizeTtl(Integer ttlSeconds) {
        if (ttlSeconds == null) {
            return DEFAULT_TTL_SECONDS;
        }
        return Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, ttlSeconds));
    }

    private String resolveUserName(AnalyticsUser user) {
        if (user == null) {
            return null;
        }
        String firstName = trimToNull(user.getFirstName());
        String lastName = trimToNull(user.getLastName());
        String fullName = null;
        if (firstName != null && lastName != null) {
            fullName = firstName + " " + lastName;
        } else if (firstName != null) {
            fullName = firstName;
        } else if (lastName != null) {
            fullName = lastName;
        }
        if (fullName != null) {
            return fullName;
        }
        String username = trimToNull(user.getUsername());
        if (username != null) {
            return username;
        }
        return String.valueOf(user.getId());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private LockSnapshot toSnapshot(AnalyticsScreenEditLock lock, Long viewerId) {
        if (lock == null) {
            return LockSnapshot.none();
        }
        Instant now = Instant.now();
        long ttl = lock.getExpireAt() == null ? 0L : Math.max(0L, lock.getExpireAt().getEpochSecond() - now.getEpochSecond());
        return new LockSnapshot(
                true,
                lock.getScreenId(),
                lock.getOwnerId(),
                lock.getOwnerName(),
                viewerId != null && viewerId.equals(lock.getOwnerId()),
                lock.getRequestId(),
                lock.getAcquiredAt(),
                lock.getHeartbeatAt(),
                lock.getExpireAt(),
                ttl);
    }

    public record LockSnapshot(
            boolean active,
            Long screenId,
            Long ownerId,
            String ownerName,
            boolean mine,
            String requestId,
            Instant acquiredAt,
            Instant heartbeatAt,
            Instant expireAt,
            long ttlSeconds) {
        public static LockSnapshot none() {
            return new LockSnapshot(false, null, null, null, false, null, null, null, null, 0L);
        }
    }

    public record LockAcquireResult(Status status, LockSnapshot snapshot) {

        public static LockAcquireResult success(LockSnapshot snapshot) {
            return new LockAcquireResult(Status.SUCCESS, snapshot);
        }

        public static LockAcquireResult conflict(LockSnapshot snapshot) {
            return new LockAcquireResult(Status.CONFLICT, snapshot);
        }

        public static LockAcquireResult notFound() {
            return new LockAcquireResult(Status.NOT_FOUND, LockSnapshot.none());
        }

        public static LockAcquireResult invalid() {
            return new LockAcquireResult(Status.INVALID, LockSnapshot.none());
        }
    }

    public enum Status {
        SUCCESS,
        CONFLICT,
        NOT_FOUND,
        INVALID,
    }
}
