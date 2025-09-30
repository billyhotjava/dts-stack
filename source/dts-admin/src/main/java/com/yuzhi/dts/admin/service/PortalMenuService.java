package com.yuzhi.dts.admin.service;

import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PortalMenuService {

    private final PortalMenuRepository repo;

    public PortalMenuService(PortalMenuRepository repo) {
        this.repo = repo;
    }

    public List<PortalMenu> findTree() {
        List<PortalMenu> roots = repo.findByParentIsNullOrderBySortOrderAscIdAsc();
        roots.forEach(this::touch);
        return roots;
    }

    private void touch(PortalMenu p) { if (p.getChildren() != null) p.getChildren().forEach(this::touch); }
}

