package com.yuzhi.dts.admin.service.workflow;

import com.yuzhi.dts.admin.domain.AdminWorkflowStep;
import com.yuzhi.dts.admin.domain.AdminWorkflowTemplate;
import com.yuzhi.dts.admin.repository.AdminWorkflowStepRepository;
import com.yuzhi.dts.admin.repository.AdminWorkflowTemplateRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AdminWorkflowConfigService {

    private static final int MAX_STEPS = 10;

    private final AdminWorkflowTemplateRepository templateRepository;
    private final AdminWorkflowStepRepository stepRepository;

    public AdminWorkflowConfigService(
        AdminWorkflowTemplateRepository templateRepository,
        AdminWorkflowStepRepository stepRepository
    ) {
        this.templateRepository = templateRepository;
        this.stepRepository = stepRepository;
    }

    public record WorkflowStepDto(Integer stepOrder, String approverRole, Boolean deptBinding) {}

    public record WorkflowTemplateDto(
        UUID id,
        String workflowType,
        String name,
        Boolean enabled,
        Integer priority,
        String ownerScope,
        String classificationMin,
        String classificationMax,
        List<WorkflowStepDto> steps
    ) {}

    public record UpsertWorkflowTemplatePayload(
        String workflowType,
        String name,
        Boolean enabled,
        Integer priority,
        String ownerScope,
        String classificationMin,
        String classificationMax,
        List<WorkflowStepDto> steps
    ) {}

    @Transactional(readOnly = true)
    public List<WorkflowTemplateDto> listTemplates(String workflowType, boolean onlyEnabled) {
        String type = normalizeType(workflowType);
        if (!StringUtils.hasText(type)) {
            return List.of();
        }
        List<AdminWorkflowTemplate> templates = onlyEnabled
            ? templateRepository.findByWorkflowTypeIgnoreCaseAndEnabledTrueOrderByPriorityDescCreatedDateDesc(type)
            : templateRepository.findByWorkflowTypeIgnoreCaseOrderByPriorityDescCreatedDateDesc(type);

        if (templates.isEmpty()) return List.of();

        Map<UUID, List<AdminWorkflowStep>> stepsByTemplate = new LinkedHashMap<>();
        for (AdminWorkflowTemplate template : templates) {
            stepsByTemplate.put(template.getId(), stepRepository.findByTemplateIdOrderByStepOrderAsc(template.getId()));
        }

        List<WorkflowTemplateDto> result = new ArrayList<>(templates.size());
        for (AdminWorkflowTemplate template : templates) {
            List<WorkflowStepDto> steps = stepsByTemplate
                .getOrDefault(template.getId(), List.of())
                .stream()
                .map(this::toDto)
                .toList();
            result.add(toDto(template, steps));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowTemplateDto> getTemplate(UUID id) {
        if (id == null) return Optional.empty();
        return templateRepository
            .findById(id)
            .map(t -> {
                List<WorkflowStepDto> steps = stepRepository.findByTemplateIdOrderByStepOrderAsc(id).stream().map(this::toDto).toList();
                return toDto(t, steps);
            });
    }

    public WorkflowTemplateDto create(UpsertWorkflowTemplatePayload payload) {
        Objects.requireNonNull(payload, "payload is required");
        validatePayload(payload);
        AdminWorkflowTemplate template = new AdminWorkflowTemplate();
        apply(template, payload);
        template = templateRepository.save(template);
        replaceSteps(template.getId(), payload.steps());
        return getTemplate(template.getId()).orElseThrow();
    }

    public WorkflowTemplateDto update(UUID id, UpsertWorkflowTemplatePayload payload) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(payload, "payload is required");
        validatePayload(payload);
        AdminWorkflowTemplate template = templateRepository.findById(id).orElseThrow();
        apply(template, payload);
        templateRepository.save(template);
        replaceSteps(id, payload.steps());
        return getTemplate(id).orElseThrow();
    }

    public void delete(UUID id) {
        if (id == null) return;
        stepRepository.deleteByTemplateId(id);
        templateRepository.deleteById(id);
    }

    private void apply(AdminWorkflowTemplate template, UpsertWorkflowTemplatePayload payload) {
        template.setWorkflowType(normalizeType(payload.workflowType()));
        template.setName(payload.name().trim());
        template.setEnabled(payload.enabled() == null ? Boolean.TRUE : Boolean.valueOf(payload.enabled()));
        template.setPriority(payload.priority() == null ? 0 : payload.priority());
        template.setOwnerScope(normalizeScope(payload.ownerScope()));
        template.setClassificationMin(trimToNull(payload.classificationMin()));
        template.setClassificationMax(trimToNull(payload.classificationMax()));
    }

    private void replaceSteps(UUID templateId, List<WorkflowStepDto> steps) {
        stepRepository.deleteByTemplateId(templateId);
        List<WorkflowStepDto> normalized = normalizeSteps(steps);
        if (normalized.isEmpty()) {
            return;
        }
        List<AdminWorkflowStep> entities = new ArrayList<>(normalized.size());
        for (WorkflowStepDto dto : normalized) {
            AdminWorkflowStep step = new AdminWorkflowStep();
            step.setTemplateId(templateId);
            step.setStepOrder(dto.stepOrder());
            step.setApproverRole(dto.approverRole().trim());
            step.setDeptBinding(dto.deptBinding() != null && dto.deptBinding().booleanValue());
            entities.add(step);
        }
        stepRepository.saveAll(entities);
    }

    private void validatePayload(UpsertWorkflowTemplatePayload payload) {
        if (!StringUtils.hasText(payload.workflowType())) {
            throw new IllegalArgumentException("workflowType is required");
        }
        if (!StringUtils.hasText(payload.name())) {
            throw new IllegalArgumentException("name is required");
        }
        List<WorkflowStepDto> normalizedSteps = normalizeSteps(payload.steps());
        if (normalizedSteps.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个审批节点");
        }
    }

    private List<WorkflowStepDto> normalizeSteps(List<WorkflowStepDto> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<WorkflowStepDto> list = new ArrayList<>();
        for (WorkflowStepDto step : steps) {
            if (step == null) continue;
            String role = trimToNull(step.approverRole());
            if (!StringUtils.hasText(role)) continue;
            boolean deptBinding = step.deptBinding() != null && step.deptBinding().booleanValue();
            list.add(new WorkflowStepDto(step.stepOrder(), role, deptBinding));
        }
        if (list.size() > MAX_STEPS) {
            throw new IllegalArgumentException("审批节点过多（最多 " + MAX_STEPS + " 个）");
        }
        // Reassign step order as 1..n if missing/invalid
        list.sort(Comparator.comparingInt(s -> s.stepOrder() == null ? Integer.MAX_VALUE : s.stepOrder().intValue()));
        List<WorkflowStepDto> normalized = new ArrayList<>(list.size());
        int i = 1;
        for (WorkflowStepDto step : list) {
            normalized.add(new WorkflowStepDto(i++, step.approverRole(), step.deptBinding()));
        }
        return normalized;
    }

    private WorkflowTemplateDto toDto(AdminWorkflowTemplate template, List<WorkflowStepDto> steps) {
        return new WorkflowTemplateDto(
            template.getId(),
            template.getWorkflowType(),
            template.getName(),
            template.getEnabled(),
            template.getPriority(),
            template.getOwnerScope(),
            template.getClassificationMin(),
            template.getClassificationMax(),
            steps == null ? List.of() : steps
        );
    }

    private WorkflowStepDto toDto(AdminWorkflowStep step) {
        return new WorkflowStepDto(step.getStepOrder(), step.getApproverRole(), step.getDeptBinding());
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) return null;
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) return "ANY";
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ANY", "INST", "DEPT" -> normalized;
            default -> "ANY";
        };
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

