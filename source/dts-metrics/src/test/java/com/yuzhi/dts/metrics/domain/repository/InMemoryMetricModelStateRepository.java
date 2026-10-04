package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.MetricModelState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Test-only in-memory {@link MetricModelStateRepository} mirroring the old {@code modelStates}
 * {@code ConcurrentHashMap} (keyed by model id, last-write-wins). Lets the resource/service unit tests
 * exercise the persisted lifecycle without a Spring context or database. Only {@code save}/{@code findById}
 * carry real behavior; the rest are unsupported.
 */
public class InMemoryMetricModelStateRepository implements MetricModelStateRepository {

    private final Map<String, MetricModelState> store = new LinkedHashMap<>();

    @Override
    public <S extends MetricModelState> S save(S entity) {
        store.put(entity.getModelId(), entity);
        return entity;
    }

    @Override
    public Optional<MetricModelState> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(String id) {
        return store.containsKey(id);
    }

    @Override
    public List<MetricModelState> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public void delete(MetricModelState entity) {
        store.remove(entity.getModelId());
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    @Override
    public <S extends MetricModelState> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public void flush() {
        // no-op for the in-memory fake
    }

    // ---- Unused surface ----

    @Override
    public <S extends MetricModelState> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<MetricModelState> findAllById(Iterable<String> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAll(Iterable<? extends MetricModelState> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<MetricModelState> findAll(Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public Page<MetricModelState> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllInBatch(Iterable<MetricModelState> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<String> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllInBatch() {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricModelState getOne(String id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricModelState getById(String id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public MetricModelState getReferenceById(String id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> long count(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends MetricModelState, R> R findBy(
        Example<S> example,
        java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction
    ) {
        throw new UnsupportedOperationException("not needed by tests");
    }
}
