package com.yuzhi.dts.admin.service;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganizationService {

    private final OrganizationRepository repository;

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public List<OrganizationNode> findTree() {
        // eager fetch children lazily via serialization; we build a detached tree
        List<OrganizationNode> roots = repository.findByParentIsNullOrderByIdAsc();
        // Force initialize by traversing
        roots.forEach(this::touch);
        return roots;
    }

    private void touch(OrganizationNode n) {
        if (n.getChildren() != null) {
            for (OrganizationNode c : n.getChildren()) touch(c);
        }
    }

    public OrganizationNode create(String name, String dataLevel, Long parentId, String contact, String phone, String description) {
        OrganizationNode entity = new OrganizationNode();
        entity.setName(name);
        entity.setDataLevel(dataLevel);
        entity.setContact(contact);
        entity.setPhone(phone);
        entity.setDescription(description);
        if (parentId != null) {
            OrganizationNode parent = repository.findById(parentId).orElseThrow();
            entity.setParent(parent);
            parent.getChildren().add(entity);
        }
        return repository.save(entity);
    }

    public Optional<OrganizationNode> update(Long id, String name, String dataLevel, String contact, String phone, String description) {
        return repository
            .findById(id)
            .map(e -> {
                e.setName(name);
                e.setDataLevel(dataLevel);
                e.setContact(contact);
                e.setPhone(phone);
                e.setDescription(description);
                return e;
            });
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}

