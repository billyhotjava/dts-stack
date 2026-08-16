package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetAccessRegistrationService;
import com.yuzhi.dts.platform.service.permission.AssetAccessRegistrationService.RegistrationCommand;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asset-ownership")
public class AssetOwnershipResource {

    private static final Logger log = LoggerFactory.getLogger(AssetOwnershipResource.class);

    private static final String INST_MANAGER_EXPRESSION =
        "hasAnyAuthority('ROLE_ADMIN', 'ROLE_OP_ADMIN', 'ROLE_INST_DATA_OWNER')";

    private final AssetOwnershipRepository ownershipRepository;
    private final AssetPermissionAuditService auditService;
    private final AssetAccessRegistrationService registrationService;

    public AssetOwnershipResource(AssetOwnershipRepository ownershipRepository,
                                   AssetPermissionAuditService auditService,
                                   AssetAccessRegistrationService registrationService) {
        this.ownershipRepository = ownershipRepository;
        this.auditService = auditService;
        this.registrationService = registrationService;
    }

    @GetMapping
    @PreAuthorize(INST_MANAGER_EXPRESSION)
    public ResponseEntity<Page<AssetOwnership>> list(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String ownerDeptCode,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        Page<AssetOwnership> page = ownershipRepository.findByFilters(assetType, ownerDeptCode, keyword, pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize(INST_MANAGER_EXPRESSION)
    public ResponseEntity<AssetOwnership> update(@PathVariable Long id, @RequestBody UpdateOwnershipRequest request) {
        return ownershipRepository.findById(id)
            .map(ownership -> {
                String oldDept = ownership.getOwnerDeptCode();
                ownership.setOwnerDeptCode(request.ownerDeptCode());
                ownership.setAssignedBy(request.assignedBy());
                ownershipRepository.save(ownership);
                auditService.recordOwnershipChange(
                    ownership.getAssetType(), ownership.getAssetId(), oldDept, request.ownerDeptCode()
                );
                return ResponseEntity.ok(ownership);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/batch")
    @Transactional
    @PreAuthorize(INST_MANAGER_EXPRESSION)
    public ResponseEntity<BatchResult> batchUpdate(@RequestBody BatchOwnershipRequest request) {
        int updated = 0;
        for (Long id : request.ids()) {
            boolean changed = ownershipRepository.findById(id).map(ownership -> {
                String oldDept = ownership.getOwnerDeptCode();
                ownership.setOwnerDeptCode(request.ownerDeptCode());
                ownership.setAssignedBy(request.assignedBy());
                ownershipRepository.save(ownership);
                auditService.recordOwnershipChange(
                    ownership.getAssetType(), ownership.getAssetId(), oldDept, request.ownerDeptCode()
                );
                return true;
            }).orElse(false);
            if (changed) {
                updated++;
            }
        }
        return ResponseEntity.ok(new BatchResult(updated));
    }

    @PostMapping
    @Transactional
    @PreAuthorize(INST_MANAGER_EXPRESSION)
    public ResponseEntity<AssetOwnership> create(@RequestBody CreateOwnershipRequest request) {
        if (request.assetType() == null || request.assetType().isBlank()
                || request.assetId() == null || request.assetId().isBlank()
                || request.ownerDeptCode() == null || request.ownerDeptCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AssetOwnership ownership = registrationService
            .register(
                new RegistrationCommand(
                    request.assetType(),
                    request.assetId(),
                    request.ownerDeptCode(),
                    null,
                    request.assignedBy(),
                    null
                )
            )
            .ownership();

        log.info("Created/updated ownership: type={} id={} dept={}",
            ownership.getAssetType(), ownership.getAssetId(), ownership.getOwnerDeptCode());
        return ResponseEntity.ok(ownership);
    }

    @DeleteMapping
    @Transactional
    @PreAuthorize(INST_MANAGER_EXPRESSION)
    public ResponseEntity<?> delete(
            @RequestParam String assetType,
            @RequestParam String assetId) {
        ownershipRepository.findByAssetTypeAndAssetId(assetType.trim(), assetId.trim())
            .ifPresent(ownership -> {
                auditService.recordOwnershipChange(
                    ownership.getAssetType(), ownership.getAssetId(),
                    ownership.getOwnerDeptCode(), null
                );
                ownershipRepository.delete(ownership);
                log.info("Deleted ownership: type={} id={}", assetType, assetId);
            });
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    public record CreateOwnershipRequest(String assetType, String assetId, String ownerDeptCode, String assignedBy) {}
    public record UpdateOwnershipRequest(String ownerDeptCode, String assignedBy) {}
    public record BatchOwnershipRequest(List<Long> ids, String ownerDeptCode, String assignedBy) {}
    public record BatchResult(int updated) {}
}
