package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.sql.SavedQuery;
import com.yuzhi.dts.platform.repository.sql.SavedQueryRepository;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryResponse;
import java.security.Principal;
import java.time.Instant;
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
        String username = principal != null ? principal.getName() : "anonymous";

        SavedQuery entity = new SavedQuery();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSqlText(request.sqlText());
        entity.setDatasourceId(request.datasourceId());
        entity.setDatasourceName(request.datasourceName());
        entity.setCreatedBy(username);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        SavedQuery saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public SavedQueryResponse update(UUID id, SavedQueryRequest request, Principal principal) {
        SavedQuery entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询不存在"));

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSqlText(request.sqlText());
        entity.setDatasourceId(request.datasourceId());
        entity.setDatasourceName(request.datasourceName());
        entity.setUpdatedAt(Instant.now());

        SavedQuery saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedQueryResponse> list(Principal principal) {
        // 返回所有用户的保存查询，方便共享
        return repository.findAllByOrderByUpdatedAtDesc()
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
            entity.getName(),
            entity.getDescription(),
            entity.getSqlText(),
            entity.getDatasourceId(),
            entity.getDatasourceName(),
            entity.getCreatedBy(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
