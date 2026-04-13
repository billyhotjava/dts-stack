package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.SavedQuery;
import com.yuzhi.dts.platform.repository.explore.SavedQueryRepository;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryResponse;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedQueryService {

    private final SavedQueryRepository repository;

    public SavedQueryService(SavedQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SavedQueryResponse save(SavedQueryRequest request, Principal principal) {
        SavedQuery entity = new SavedQuery();
        entity.setTitle(request.name());
        entity.setSqlText(request.sqlText());
        entity.setEngine(ExecEnums.ExecEngine.TRINO);
        entity.setLevel(ExecEnums.SecurityLevel.INTERNAL);
        entity.setConnection(request.datasourceName());
        entity.setTags(request.description());
        entity.setFolder(request.folder());

        SavedQuery saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public SavedQueryResponse update(UUID id, SavedQueryRequest request, Principal principal) {
        SavedQuery entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询不存在"));

        entity.setTitle(request.name());
        entity.setSqlText(request.sqlText());
        entity.setConnection(request.datasourceName());
        entity.setTags(request.description());
        entity.setFolder(request.folder());

        SavedQuery saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedQueryResponse> list(Principal principal) {
        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public SavedQueryResponse get(UUID id) {
        SavedQuery entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询不存在"));
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id, Principal principal) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查询不存在");
        }
        repository.deleteById(id);
    }

    private SavedQueryResponse toResponse(SavedQuery entity) {
        return new SavedQueryResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getTags(),
            entity.getSqlText(),
            null,
            entity.getConnection(),
            entity.getCreatedBy(),
            entity.getCreatedDate() != null ? entity.getCreatedDate().toString() : null,
            entity.getLastModifiedDate() != null ? entity.getLastModifiedDate().toString() : null,
            entity.getFolder()
        );
    }
}
