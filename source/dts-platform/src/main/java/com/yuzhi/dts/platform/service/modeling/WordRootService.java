package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingWordRoot;
import com.yuzhi.dts.platform.repository.modeling.ModelingWordRootRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.modeling.WordRootContract.UpsertRequest;
import com.yuzhi.dts.platform.service.modeling.WordRootContract.View;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class WordRootService {

    private static final int LIST_LIMIT = 500;

    private final ModelingWordRootRepository repository;
    private final DataStandardSecurity security;
    private final OrganizationVisibilityService organizationVisibility;

    public WordRootService(
        ModelingWordRootRepository repository,
        DataStandardSecurity security,
        OrganizationVisibilityService organizationVisibility
    ) {
        this.repository = repository;
        this.security = security;
        this.organizationVisibility = organizationVisibility;
    }

    @Transactional(readOnly = true)
    public List<View> list(String keyword, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        String normalizedKeyword = trimToNull(keyword);
        Specification<ModelingWordRoot> filter = Specification.where(null);
        if (normalizedKeyword != null) {
            String like = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
            filter = filter.and((root, query, builder) ->
                builder.or(
                    builder.like(builder.lower(root.get("code")), like),
                    builder.like(builder.lower(root.get("nameCn")), like),
                    builder.like(builder.lower(root.get("nameEn")), like),
                    builder.like(builder.lower(root.get("abbreviation")), like)
                )
            );
        }
        return repository
            .findAll(filter, PageRequest.of(0, LIST_LIMIT, Sort.by("nameCn").ascending()))
            .stream()
            .filter(root -> isVisible(root.getOwnerDept(), activeDept, instituteScope))
            .map(WordRootService::toView)
            .toList();
    }

    public View create(UpsertRequest request, String activeDeptHeader) {
        ModelingWordRoot root = new ModelingWordRoot();
        applyUpsert(root, request, true);
        root.setOwnerDept(resolveOwnerDept(activeDeptHeader));
        return toView(repository.save(root));
    }

    public View update(UUID id, UpsertRequest request, String activeDeptHeader) {
        ModelingWordRoot root = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("词根不存在"));
        ensureWritable(root, activeDeptHeader);
        applyUpsert(root, request, false);
        return toView(repository.save(root));
    }

    private void applyUpsert(ModelingWordRoot root, UpsertRequest request, boolean creating) {
        if (creating) {
            root.setCode(normalizeAsciiCode(request.code()));
        }
        root.setNameCn(requiredText(request.nameCn()));
        root.setNameEn(requiredText(request.nameEn()));
        root.setAbbreviation(normalizeAsciiCode(request.abbreviation()));
        root.setDomain(trimToNull(request.domain()));
        root.setVersion(StringUtils.hasText(request.version()) ? request.version().trim() : "v1");
        root.setStatus("ACTIVE");
    }

    private String resolveOwnerDept(String activeDeptHeader) {
        String activeDept = trimToNull(security.resolveActiveDept(activeDeptHeader));
        if (activeDept != null) {
            return activeDept;
        }
        if (security.hasInstituteScope()) {
            return organizationVisibility.resolveDefaultRootDept().orElse(null);
        }
        throw new AccessDeniedException("当前账号未配置所属部门，无法维护词根");
    }

    private void ensureWritable(ModelingWordRoot root, String activeDeptHeader) {
        if (security.hasInstituteScope()) {
            return;
        }
        String activeDept = trimToNull(security.resolveActiveDept(activeDeptHeader));
        if (activeDept == null) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法维护词根");
        }
        String ownerDept = trimToNull(root.getOwnerDept());
        if (ownerDept != null && !DepartmentUtils.matches(ownerDept, activeDept)) {
            throw new AccessDeniedException("仅允许维护当前登录部门的词根");
        }
        if (ownerDept == null) {
            root.setOwnerDept(activeDept);
        }
    }

    private boolean isVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String normalizedOwner = trimToNull(ownerDept);
        if (normalizedOwner == null || instituteScope || organizationVisibility.isRoot(normalizedOwner)) {
            return true;
        }
        return StringUtils.hasText(activeDept) && DepartmentUtils.matches(normalizedOwner, activeDept);
    }

    private static View toView(ModelingWordRoot root) {
        return new View(
            root.getId(),
            root.getCode(),
            root.getNameCn(),
            root.getNameEn(),
            root.getAbbreviation(),
            root.getDomain(),
            root.getVersion(),
            root.getStatus(),
            root.getOwnerDept()
        );
    }

    private static String normalizeAsciiCode(String value) {
        return requiredText(value).toUpperCase(Locale.ROOT);
    }

    private static String requiredText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("词根必填信息不能为空");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
