package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.GraphDraft;
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
 * Test-only in-memory {@link GraphDraftRepository} mirroring the old {@code ConcurrentHashMap drafts}
 * semantics (insertion-ordered, keyed by draft id). Lets {@code MetricGraphResourceTest} construct
 * {@code MetricGraphDraftService} without a Spring context or database. Only the methods the service
 * touches ({@code save}/{@code findById}) carry real behavior; the rest are unsupported.
 */
public class InMemoryGraphDraftRepository implements GraphDraftRepository {

    private final Map<String, GraphDraft> store = new LinkedHashMap<>();

    @Override
    public <S extends GraphDraft> S save(S entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<GraphDraft> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(String id) {
        return store.containsKey(id);
    }

    @Override
    public List<GraphDraft> findAll() {
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
    public void delete(GraphDraft entity) {
        store.remove(entity.getId());
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    // ---- Unused surface: GraphDraftRepository only needs findById/save in T03. ----

    @Override
    public <S extends GraphDraft> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<GraphDraft> findAllById(Iterable<String> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAll(Iterable<? extends GraphDraft> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public List<GraphDraft> findAll(Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public Page<GraphDraft> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> S saveAndFlush(S entity) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public void deleteAllInBatch(Iterable<GraphDraft> entities) {
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
    public GraphDraft getOne(String id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public GraphDraft getById(String id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public GraphDraft getReferenceById(String id) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> long count(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("not needed by tests");
    }

    @Override
    public <S extends GraphDraft, R> R findBy(
        Example<S> example,
        java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction
    ) {
        throw new UnsupportedOperationException("not needed by tests");
    }
}
