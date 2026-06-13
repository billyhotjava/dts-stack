package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.MetricModelVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Test-only in-memory {@link MetricModelVersionRepository} mirroring the old ordered {@code modelVersions}
 * List. Reproduces the {@code (model_id, version)} unique constraint so a duplicate insert throws
 * {@link DataIntegrityViolationException} — letting the service's 409 {@code metric_version_conflict}
 * mapping be exercised without a database. Assigns the surrogate UUID + initial {@code version_lock} on
 * save, like the JPA provider would.
 */
public class InMemoryMetricModelVersionRepository implements MetricModelVersionRepository {

    private final Map<UUID, MetricModelVersion> store = new LinkedHashMap<>();

    @Override
    public List<MetricModelVersion> findByModelIdOrderByVersionOrdinalAsc(String modelId) {
        return store
            .values()
            .stream()
            .filter(version -> modelId.equals(version.getModelId()))
            .sorted(Comparator.comparingInt(MetricModelVersion::getVersionOrdinal))
            .toList();
    }

    @Override
    public long countByModelId(String modelId) {
        return store.values().stream().filter(version -> modelId.equals(version.getModelId())).count();
    }

    @Override
    public <S extends MetricModelVersion> S save(S entity) {
        boolean duplicate = store
            .values()
            .stream()
            .anyMatch(existing ->
                existing.getModelId() != null &&
                existing.getModelId().equals(entity.getModelId()) &&
                existing.getVersion() != null &&
                existing.getVersion().equals(entity.getVersion()) &&
                !sameRow(existing, entity)
            );
        if (duplicate) {
            throw new DataIntegrityViolationException("uk_metric_model_version violated: " + entity.getModelId() + "/" + entity.getVersion());
        }
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        if (entity.getVersionLock() == null) {
            entity.setVersionLock(0L);
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public <S extends MetricModelVersion> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public void flush() {
        // no-op for the in-memory fake
    }

    private static boolean sameRow(MetricModelVersion existing, MetricModelVersion candidate) {
        return existing.getId() != null && existing.getId().equals(candidate.getId());
    }

    @Override
    public Optional<MetricModelVersion> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    @Override
    public List<MetricModelVersion> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    @Override
    public void delete(MetricModelVersion entity) {
        store.remove(entity.getId());
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    // ---- Unused surface ----

    @Override
    public <S extends MetricModelVersion> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<MetricModelVersion> findAllById(Iterable<UUID> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAll(Iterable<? extends MetricModelVersion> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<MetricModelVersion> findAll(Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public Page<MetricModelVersion> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllInBatch(Iterable<MetricModelVersion> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<UUID> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllInBatch() {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricModelVersion getOne(UUID id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricModelVersion getById(UUID id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricModelVersion getReferenceById(UUID id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> long count(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelVersion, R> R findBy(
        Example<S> example,
        java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction
    ) {
        throw new UnsupportedOperationException("not needed by tests");
    }
}
