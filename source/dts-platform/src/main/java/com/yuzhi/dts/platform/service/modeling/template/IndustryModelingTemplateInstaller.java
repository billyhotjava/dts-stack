package com.yuzhi.dts.platform.service.modeling.template;

import com.yuzhi.dts.platform.service.modeling.template.IndustryModelingTemplateContract.IndustryModelingTemplate;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Copies an explicitly selected template into domain-owned governance records. */
@Service
@Transactional
public class IndustryModelingTemplateInstaller {

    private static final String TEMPLATE_SOURCE_TYPE = "TEMPLATE";

    private final NamedParameterJdbcTemplate jdbc;

    public IndustryModelingTemplateInstaller(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InstallationResult install(UUID domainId, IndustryModelingTemplate template) {
        Objects.requireNonNull(domainId, "domainId");
        Objects.requireNonNull(template, "template");
        Timestamp now = Timestamp.from(java.time.Instant.now());
        int createdProcesses = template
            .businessProcesses()
            .stream()
            .mapToInt(process ->
                jdbc.update(
                    """
                    insert into sprint64_business_process
                        (id, domain_id, version, process_id, name, description,
                         source_type, source_id, source_version, confirmed, created_date, last_modified_date)
                    values
                        (:id, :domainId, :contractVersion, :processId, :name, :description,
                         :sourceType, :sourceId, :sourceVersion, false, :now, :now)
                    on conflict (domain_id, process_id) do nothing
                    """,
                    provenanceParams(domainId, template, now)
                        .addValue("id", UUID.randomUUID())
                        .addValue("contractVersion", Sprint64GovernanceContract.VERSION)
                        .addValue("processId", process.processId())
                        .addValue("name", process.name())
                        .addValue("description", process.description())
                )
            )
            .sum();

        int createdDimensions = template
            .conformedDimensions()
            .stream()
            .mapToInt(dimension ->
                jdbc.update(
                    """
                    insert into sprint64_conformed_dimension
                        (domain_id, dimension_id, name, source_model,
                         source_type, source_id, source_version, confirmed, created_date, last_modified_date)
                    values
                        (:domainId, :dimensionId, :name, :sourceModel,
                         :sourceType, :sourceId, :sourceVersion, false, :now, :now)
                    on conflict (domain_id, dimension_id) do nothing
                    """,
                    provenanceParams(domainId, template, now)
                        .addValue("dimensionId", dimension.dimensionId())
                        .addValue("name", dimension.name())
                        .addValue("sourceModel", dimension.sourceModel())
                )
            )
            .sum();

        InstallationStatus status = createdProcesses + createdDimensions == 0
            ? InstallationStatus.ALREADY_INSTALLED
            : InstallationStatus.INSTALLED;
        return new InstallationResult(
            domainId,
            template.templateId(),
            template.version(),
            status,
            createdProcesses,
            createdDimensions
        );
    }

    private static MapSqlParameterSource provenanceParams(UUID domainId, IndustryModelingTemplate template, Timestamp now) {
        return new MapSqlParameterSource()
            .addValue("domainId", domainId)
            .addValue("sourceType", TEMPLATE_SOURCE_TYPE)
            .addValue("sourceId", template.templateId())
            .addValue("sourceVersion", template.version())
            .addValue("now", now);
    }

    public enum InstallationStatus {
        INSTALLED,
        ALREADY_INSTALLED,
    }

    public record InstallationResult(
        UUID domainId,
        String templateId,
        String templateVersion,
        InstallationStatus status,
        int createdProcesses,
        int createdDimensions
    ) {}
}
