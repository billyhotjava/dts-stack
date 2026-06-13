package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.MetricRollbackEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Test-only in-memory {@link MetricRollbackEventRepository} mirroring the old ordered {@code rollbackEvents}
 * List. {@code countByModelId} feeds {@code eventVersion = "rollback-" + (count + 1)};
 * {@code findByModelIdOrderByEventOrdinalAsc} reproduces the ordered event history. Assigns the surrogate
 * UUID on save like the JPA provider would.
 */
public class InMemoryMetricRollbackEventRepository implements MetricRollbackEventRepository {

    private final Map<UUID, MetricRollbackEvent> store = new LinkedHashMap<>();

    @Override
    public List<MetricRollbackEvent> findByModelIdOrderByEventOrdinalAsc(String modelId) {
        return store
            .values()
            .stream()
            .filter(event -> modelId.equals(event.getModelId()))
            .sorted(Comparator.comparingInt(MetricRollbackEvent::getEventOrdinal))
            .toList();
    }

    @Override
    public long countByModelId(String modelId) {
        return store.values().stream().filter(event -> modelId.equals(event.getModelId())).count();
    }

    @Override
    public <S extends MetricRollbackEvent> S save(S entity) {
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public <S extends MetricRollbackEvent> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public void flush() {
        // no-op for the in-memory fake
    }

    @Override
    public Optional<MetricRollbackEvent> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    @Override
    public List<MetricRollbackEvent> findAll() {
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
    public void delete(MetricRollbackEvent entity) {
        store.remove(entity.getId());
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    // ---- Unused surface ----

    @Override
    public <S extends MetricRollbackEvent> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<MetricRollbackEvent> findAllById(Iterable<UUID> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAll(Iterable<? extends MetricRollbackEvent> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<MetricRollbackEvent> findAll(Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public Page<MetricRollbackEvent> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllInBatch(Iterable<MetricRollbackEvent> entities) {
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
    public MetricRollbackEvent getOne(UUID id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricRollbackEvent getById(UUID id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricRollbackEvent getReferenceById(UUID id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> long count(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricRollbackEvent, R> R findBy(
        Example<S> example,
        java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction
    ) {
        throw new UnsupportedOperationException("not needed by tests");
    }
}
