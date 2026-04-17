package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.SavedQuery;
import com.yuzhi.dts.platform.repository.explore.SavedQueryRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryResponse;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
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
        assertOwnerOrPrivileged(entity, principal);

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
        // Privileged roles (admin/op-admin/institute-level) see every saved query;
        // everyone else only sees their own. Prior behaviour returned the full table,
        // leaking private SQL across users.
        List<SavedQuery> entities = isPrivileged()
            ? repository.findAll()
            : repository.findByCreatedByOrderByLastModifiedDateDesc(currentLogin(principal));
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SavedQueryResponse get(UUID id) {
        SavedQuery entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询不存在"));
        assertOwnerOrPrivileged(entity, null);
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id, Principal principal) {
        SavedQuery entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询不存在"));
        assertOwnerOrPrivileged(entity, principal);
        repository.deleteById(id);
    }

    private void assertOwnerOrPrivileged(SavedQuery entity, Principal principal) {
        if (isPrivileged()) return;
        String owner = entity.getCreatedBy();
        String me = currentLogin(principal);
        if (owner == null || !Objects.equals(owner, me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该查询");
        }
    }

    private boolean isPrivileged() {
        for (String role : AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES) {
            if (SecurityUtils.hasCurrentUserAnyOfAuthorities(role)) {
                return true;
            }
        }
        return false;
    }

    private String currentLogin(Principal principal) {
        if (principal != null && principal.getName() != null) {
            return principal.getName();
        }
        return SecurityUtils.getCurrentUserLogin().orElse("anonymous");
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
