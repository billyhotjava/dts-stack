package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.governance.GovComplianceCheck;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.repository.governance.GovComplianceCheckRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/governance")
@Transactional
public class GovernanceResource {

    private final GovRuleRepository ruleRepo;
    private final GovComplianceCheckRepository checkRepo;
    private final AuditService audit;

    public GovernanceResource(GovRuleRepository ruleRepo, GovComplianceCheckRepository checkRepo, AuditService audit) {
        this.ruleRepo = ruleRepo;
        this.checkRepo = checkRepo;
        this.audit = audit;
    }

    @GetMapping("/rules")
    public ApiResponse<List<GovRule>> listRules() {
        List<GovRule> list = ruleRepo.findAll();
        audit.audit("READ", "governance.rule", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.GOV_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<GovRule> createRule(@RequestBody GovRule rule) {
        GovRule saved = ruleRepo.save(rule);
        audit.audit("CREATE", "governance.rule", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.GOV_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<GovRule> updateRule(@PathVariable UUID id, @RequestBody GovRule patch) {
        GovRule existing = ruleRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setType(patch.getType());
        existing.setExpression(patch.getExpression());
        existing.setDatasetId(patch.getDatasetId());
        existing.setEnabled(patch.getEnabled());
        GovRule saved = ruleRepo.save(existing);
        audit.audit("UPDATE", "governance.rule", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.GOV_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Boolean> deleteRule(@PathVariable UUID id) {
        ruleRepo.deleteById(id);
        audit.audit("DELETE", "governance.rule", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/rules/{id}/toggle")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.GOV_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<GovRule> toggle(@PathVariable UUID id) {
        GovRule rule = ruleRepo.findById(id).orElseThrow();
        rule.setEnabled(Boolean.FALSE.equals(rule.getEnabled()));
        GovRule saved = ruleRepo.save(rule);
        audit.audit("UPDATE", "governance.rule.toggle", id.toString());
        return ApiResponses.ok(saved);
    }

    @GetMapping("/compliance-checks")
    public ApiResponse<List<GovComplianceCheck>> listChecks() {
        List<GovComplianceCheck> list = checkRepo.findAll();
        audit.audit("READ", "governance.compliance", "list");
        return ApiResponses.ok(list);
    }

    @GetMapping("/compliance-checks/{id}")
    public ApiResponse<GovComplianceCheck> getCheck(@PathVariable UUID id) {
        GovComplianceCheck item = checkRepo.findById(id).orElseThrow();
        audit.audit("READ", "governance.compliance", id.toString());
        return ApiResponses.ok(item);
    }

    @PostMapping("/compliance-checks/run")
    public ApiResponse<Map<String, Object>> runCheck(@RequestBody(required = false) Map<String, Object> params) {
        // simulate: pick first rule and create a check record
        List<GovRule> rules = ruleRepo.findAll();
        if (rules.isEmpty()) {
            return ApiResponses.ok(Map.of("status", "NO_RULE"));
        }
        GovRule rule = rules.get(0);
        GovComplianceCheck check = new GovComplianceCheck();
        check.setRuleId(rule.getId());
        check.setStatus("SUCCESS");
        check.setDetail("Simulated compliance check passed.");
        check.setCheckedAt(Instant.now());
        checkRepo.save(check);
        audit.audit("EXECUTE", "governance.compliance.run", rule.getId().toString());
        return ApiResponses.ok(Map.of("checkId", check.getId(), "status", check.getStatus()));
    }
}

