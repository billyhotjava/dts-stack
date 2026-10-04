package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.sql.SqlIdeTab;
import com.yuzhi.dts.platform.repository.sql.SqlIdeTabRepository;
import com.yuzhi.dts.platform.service.sql.dto.CreateTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.PatchTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlIdeTabDto;
import com.yuzhi.dts.platform.service.sql.dto.UpsertTabRequest;
import com.yuzhi.dts.platform.web.rest.errors.BatchSizeExceededException;
import com.yuzhi.dts.platform.web.rest.errors.TabConflictException;
import com.yuzhi.dts.platform.web.rest.errors.TooManyTabsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SqlIdeTabServiceImpl implements SqlIdeTabService {

    private static final int BATCH_MAX = 50;

    private final SqlIdeTabRepository repository;
    private final EntityManager em;

    public SqlIdeTabServiceImpl(SqlIdeTabRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SqlIdeTabDto> listByUser(String userLogin) {
        return repository.findByUserLoginOrderBySortOrderAsc(userLogin).stream().map(this::toDto).toList();
    }

    @Override
    public SqlIdeTabDto create(String userLogin, CreateTabRequest req) {
        if (repository.countByUserLogin(userLogin) >= MAX_TABS_PER_USER) {
            throw new TooManyTabsException();
        }
        SqlIdeTab tab = new SqlIdeTab();
        tab.setUserLogin(userLogin);
        tab.setTitle(req.title());
        tab.setSqlText(req.sqlText());
        tab.setEngine(req.engine());
        tab.setDatasourceId(req.datasourceId());
        tab.setSchemaCtx(req.schemaCtx());
        tab.setCursorLine(req.cursorLine());
        tab.setCursorCol(req.cursorCol());
        tab.setSelectionJson(req.selectionJson());
        tab.setLastExecutionId(req.lastExecutionId());
        tab.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        if (req.active() != null) tab.setActive(req.active());
        return toDto(saveFlushRefresh(tab));
    }

    @Override
    public SqlIdeTabDto patch(String userLogin, UUID id, PatchTabRequest req) {
        SqlIdeTab tab = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("tab not found"));
        if (!userLogin.equals(tab.getUserLogin())) {
            // Do not leak existence to other users
            throw new EntityNotFoundException("tab not found");
        }
        if (req.updatedAt() != null && tab.getLastModifiedDate() != null
            && !tab.getLastModifiedDate().equals(req.updatedAt())) {
            throw new TabConflictException("stale updatedAt");
        }
        if (req.title() != null) tab.setTitle(req.title());
        if (req.sqlText() != null) tab.setSqlText(req.sqlText());
        if (req.engine() != null) tab.setEngine(req.engine());
        if (req.datasourceId() != null) tab.setDatasourceId(req.datasourceId());
        if (req.schemaCtx() != null) tab.setSchemaCtx(req.schemaCtx());
        if (req.cursorLine() != null) tab.setCursorLine(req.cursorLine());
        if (req.cursorCol() != null) tab.setCursorCol(req.cursorCol());
        if (req.selectionJson() != null) tab.setSelectionJson(req.selectionJson());
        if (req.lastExecutionId() != null) tab.setLastExecutionId(req.lastExecutionId());
        if (req.sortOrder() != null) tab.setSortOrder(req.sortOrder());
        if (req.active() != null) tab.setActive(req.active());
        return toDto(saveFlushRefresh(tab));
    }

    @Override
    public void delete(String userLogin, UUID id) {
        repository
            .findById(id)
            .filter(t -> userLogin.equals(t.getUserLogin()))
            .ifPresent(repository::delete);
    }

    @Override
    public List<SqlIdeTabDto> batchUpsert(String userLogin, List<UpsertTabRequest> reqs) {
        if (reqs.size() > BATCH_MAX) {
            throw new BatchSizeExceededException("batch size exceeds " + BATCH_MAX);
        }
        long existing = repository.countByUserLogin(userLogin);
        long newCount = reqs.stream().filter(r -> r.id() == null).count();
        if (existing + newCount > MAX_TABS_PER_USER) {
            throw new TooManyTabsException();
        }
        return reqs.stream().map(r -> upsertOne(userLogin, r)).toList();
    }

    private SqlIdeTabDto upsertOne(String userLogin, UpsertTabRequest r) {
        if (r.id() != null) {
            PatchTabRequest patchReq = new PatchTabRequest(
                r.title(), r.sqlText(), r.engine(), r.datasourceId(), r.schemaCtx(),
                r.cursorLine(), r.cursorCol(), r.selectionJson(), r.lastExecutionId(),
                r.sortOrder(), r.active(), r.updatedAt()
            );
            return patch(userLogin, r.id(), patchReq);
        }
        CreateTabRequest createReq = new CreateTabRequest(
            r.title(), r.sqlText(), r.engine(), r.datasourceId(), r.schemaCtx(),
            r.cursorLine(), r.cursorCol(), r.selectionJson(), r.lastExecutionId(),
            r.sortOrder(), r.active()
        );
        return create(userLogin, createReq);
    }

    /**
     * Save, flush to DB, then refresh the in-memory entity from the DB so that
     * audit timestamps reflect the DB-truncated value (microseconds in Postgres).
     * This ensures the DTO returned to the client carries the exact same timestamp
     * that subsequent reads from DB will return, preventing false 409s on next PATCH.
     */
    private SqlIdeTab saveFlushRefresh(SqlIdeTab tab) {
        SqlIdeTab saved = repository.saveAndFlush(tab);
        em.refresh(saved);
        return saved;
    }

    private SqlIdeTabDto toDto(SqlIdeTab t) {
        return new SqlIdeTabDto(
            t.getId(),
            t.getTitle(),
            t.getSqlText(),
            t.getEngine(),
            t.getDatasourceId(),
            t.getSchemaCtx(),
            t.getCursorLine(),
            t.getCursorCol(),
            t.getSelectionJson(),
            t.getLastExecutionId(),
            t.getSortOrder(),
            t.isActive(),
            t.getLastModifiedDate()
        );
    }
}
