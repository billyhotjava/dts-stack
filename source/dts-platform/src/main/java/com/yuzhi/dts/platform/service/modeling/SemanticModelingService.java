package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.service.etl.DbtFileService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseSubmissionService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class SemanticModelingService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DbtFileService dbtFileService;
    private final QueryGateway queryGateway;
    private final QueryDatasetAssetRepository queryDatasetRepository;
    private final QueryDatasetVersionRepository queryDatasetVersionRepository;
    private final CatalogDatasetRepository catalogDatasetRepository;
    private final CatalogDatasetLineageRepository catalogLineageRepository;
    private final DbtReleaseSubmissionService dbtReleaseSubmissionService;

    public SemanticModelingService(
        NamedParameterJdbcTemplate jdbc,
        ObjectMapper objectMapper,
        DbtFileService dbtFileService,
        QueryGateway queryGateway,
        QueryDatasetAssetRepository queryDatasetRepository,
        QueryDatasetVersionRepository queryDatasetVersionRepository,
        CatalogDatasetRepository catalogDatasetRepository,
        CatalogDatasetLineageRepository catalogLineageRepository,
        DbtReleaseSubmissionService dbtReleaseSubmissionService
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.dbtFileService = dbtFileService;
        this.queryGateway = queryGateway;
        this.queryDatasetRepository = queryDatasetRepository;
        this.queryDatasetVersionRepository = queryDatasetVersionRepository;
        this.catalogDatasetRepository = catalogDatasetRepository;
        this.catalogLineageRepository = catalogLineageRepository;
        this.dbtReleaseSubmissionService = dbtReleaseSubmissionService;
    }

    @Transactional(readOnly = true)
    public List<SubjectDomainDto> listSubjectDomains() {
        return jdbc.query(
            """
            select id, code, name, description, status, owner_dept,
                   governance_domain_id, governance_domain_code, governance_domain_name
            from semantic_subject_domain
            order by name asc, code asc
            """,
            Map.of(),
            (rs, rowNum) -> new SubjectDomainDto(
                uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("owner_dept"),
                uuid(rs, "governance_domain_id"),
                rs.getString("governance_domain_code"),
                rs.getString("governance_domain_name")
            )
        );
    }

    public SubjectDomainDto createSubjectDomain(SubjectDomainRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_subject_domain
                (id, code, name, description, status, owner_dept,
                 governance_domain_id, governance_domain_code, governance_domain_name,
                 created_date, last_modified_date)
            values
                (:id, :code, :name, :description, :status, :ownerDept,
                 :governanceDomainId, :governanceDomainCode, :governanceDomainName,
                 :now, :now)
            """,
            params()
                .addValue("id", id)
                .addValue("code", required(request.code(), "code"))
                .addValue("name", required(request.name(), "name"))
                .addValue("description", trimToNull(request.description()))
                .addValue("status", defaultValue(request.status(), "DRAFT"))
                .addValue("ownerDept", trimToNull(request.ownerDept()))
                .addValue("governanceDomainId", request.governanceDomainId())
                .addValue("governanceDomainCode", trimToNull(request.governanceDomainCode()))
                .addValue("governanceDomainName", trimToNull(request.governanceDomainName()))
        );
        return getSubjectDomain(id);
    }

    public SubjectDomainDto updateSubjectDomain(UUID id, SubjectDomainRequest request) {
        jdbc.update(
            """
            update semantic_subject_domain
            set code = :code,
                name = :name,
                description = :description,
                status = :status,
                owner_dept = :ownerDept,
                governance_domain_id = :governanceDomainId,
                governance_domain_code = :governanceDomainCode,
                governance_domain_name = :governanceDomainName,
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", id)
                .addValue("code", required(request.code(), "code"))
                .addValue("name", required(request.name(), "name"))
                .addValue("description", trimToNull(request.description()))
                .addValue("status", defaultValue(request.status(), "DRAFT"))
                .addValue("ownerDept", trimToNull(request.ownerDept()))
                .addValue("governanceDomainId", request.governanceDomainId())
                .addValue("governanceDomainCode", trimToNull(request.governanceDomainCode()))
                .addValue("governanceDomainName", trimToNull(request.governanceDomainName()))
        );
        return getSubjectDomain(id);
    }

    @Transactional(readOnly = true)
    public List<BusinessObjectDto> listBusinessObjects(UUID domainId) {
        MapSqlParameterSource p = params().addValue("domainId", domainId);
        return jdbc.query(
            """
            select id, domain_id, code, name, description, primary_key, main_table, status, owner_dept
            from semantic_business_object
            where (:domainId is null or domain_id = :domainId)
            order by name asc, code asc
            """,
            p,
            (rs, rowNum) -> new BusinessObjectDto(
                uuid(rs, "id"),
                uuid(rs, "domain_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("primary_key"),
                rs.getString("main_table"),
                rs.getString("status"),
                rs.getString("owner_dept")
            )
        );
    }

    public BusinessObjectDto createBusinessObject(BusinessObjectRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_business_object
                (id, domain_id, code, name, description, primary_key, main_table, status, owner_dept, created_date, last_modified_date)
            values
                (:id, :domainId, :code, :name, :description, :primaryKey, :mainTable, :status, :ownerDept, :now, :now)
            """,
            params()
                .addValue("id", id)
                .addValue("domainId", request.domainId())
                .addValue("code", required(request.code(), "code"))
                .addValue("name", required(request.name(), "name"))
                .addValue("description", trimToNull(request.description()))
                .addValue("primaryKey", trimToNull(request.primaryKey()))
                .addValue("mainTable", trimToNull(request.mainTable()))
                .addValue("status", defaultValue(request.status(), "DRAFT"))
                .addValue("ownerDept", trimToNull(request.ownerDept()))
        );
        return getBusinessObject(id);
    }

    public BusinessObjectDto updateBusinessObject(UUID id, BusinessObjectRequest request) {
        jdbc.update(
            """
            update semantic_business_object
            set domain_id = :domainId,
                code = :code,
                name = :name,
                description = :description,
                primary_key = :primaryKey,
                main_table = :mainTable,
                status = :status,
                owner_dept = :ownerDept,
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", id)
                .addValue("domainId", request.domainId())
                .addValue("code", required(request.code(), "code"))
                .addValue("name", required(request.name(), "name"))
                .addValue("description", trimToNull(request.description()))
                .addValue("primaryKey", trimToNull(request.primaryKey()))
                .addValue("mainTable", trimToNull(request.mainTable()))
                .addValue("status", defaultValue(request.status(), "DRAFT"))
                .addValue("ownerDept", trimToNull(request.ownerDept()))
        );
        return getBusinessObject(id);
    }

    @Transactional(readOnly = true)
    public List<ObjectTableMappingDto> listObjectTableMappings(UUID objectId) {
        return jdbc.query(
            """
            select id, object_id, table_name, table_role, join_expression, sort_order
            from semantic_object_table_mapping
            where object_id = :objectId
            order by sort_order asc, table_name asc
            """,
            params().addValue("objectId", objectId),
            (rs, rowNum) -> new ObjectTableMappingDto(
                uuid(rs, "id"),
                uuid(rs, "object_id"),
                rs.getString("table_name"),
                rs.getString("table_role"),
                rs.getString("join_expression"),
                rs.getInt("sort_order")
            )
        );
    }

    public List<ObjectTableMappingDto> saveObjectTableMappings(UUID objectId, ObjectTableMappingSaveRequest request) {
        getBusinessObject(objectId);
        jdbc.update("delete from semantic_object_table_mapping where object_id = :objectId", params().addValue("objectId", objectId));
        List<ObjectTableMappingRequest> rows = request == null || request.mappings() == null ? List.of() : request.mappings();
        int index = 0;
        for (ObjectTableMappingRequest row : rows) {
            if (!StringUtils.hasText(row.tableName())) continue;
            jdbc.update(
                """
                insert into semantic_object_table_mapping
                    (id, object_id, table_name, table_role, join_expression, sort_order)
                values
                    (:id, :objectId, :tableName, :tableRole, :joinExpression, :sortOrder)
                """,
                params()
                    .addValue("id", UUID.randomUUID())
                    .addValue("objectId", objectId)
                    .addValue("tableName", required(row.tableName(), "tableName"))
                    .addValue("tableRole", defaultValue(row.tableRole(), index == 0 ? "PRIMARY" : "JOINED"))
                    .addValue("joinExpression", trimToNull(row.joinExpression()))
                    .addValue("sortOrder", row.sortOrder() == null ? index : row.sortOrder())
            );
            index++;
        }
        return listObjectTableMappings(objectId);
    }

    @Transactional(readOnly = true)
    public List<DimensionDto> listDimensions(UUID objectId) {
        return jdbc.query(
            """
            select id, object_id, code, name, field_name, data_type, semantic_type, status
            from semantic_dimension
            where (:objectId is null or object_id = :objectId)
            order by name asc, code asc
            """,
            params().addValue("objectId", objectId),
            (rs, rowNum) -> new DimensionDto(
                uuid(rs, "id"),
                uuid(rs, "object_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("field_name"),
                rs.getString("data_type"),
                rs.getString("semantic_type"),
                rs.getString("status")
            )
        );
    }

    public DimensionDto createDimension(DimensionRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_dimension
                (id, object_id, code, name, field_name, data_type, semantic_type, status, created_date, last_modified_date)
            values
                (:id, :objectId, :code, :name, :fieldName, :dataType, :semanticType, :status, :now, :now)
            """,
            params()
                .addValue("id", id)
                .addValue("objectId", request.objectId())
                .addValue("code", required(request.code(), "code"))
                .addValue("name", required(request.name(), "name"))
                .addValue("fieldName", trimToNull(request.fieldName()))
                .addValue("dataType", trimToNull(request.dataType()))
                .addValue("semanticType", trimToNull(request.semanticType()))
                .addValue("status", defaultValue(request.status(), "DRAFT"))
        );
        return getDimension(id);
    }

    public DimensionDto updateDimension(UUID id, DimensionRequest request) {
        jdbc.update(
            """
            update semantic_dimension
            set object_id = :objectId,
                code = :code,
                name = :name,
                field_name = :fieldName,
                data_type = :dataType,
                semantic_type = :semanticType,
                status = :status,
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", id)
                .addValue("objectId", request.objectId())
                .addValue("code", required(request.code(), "code"))
                .addValue("name", required(request.name(), "name"))
                .addValue("fieldName", trimToNull(request.fieldName()))
                .addValue("dataType", trimToNull(request.dataType()))
                .addValue("semanticType", trimToNull(request.semanticType()))
                .addValue("status", defaultValue(request.status(), "DRAFT"))
        );
        return getDimension(id);
    }

    @Transactional(readOnly = true)
    public List<MetricDto> listMetrics(UUID objectId) {
        return jdbc.query(
            """
            select id, object_id, code, name, formula_type, formula_json, format, unit, status
            from semantic_metric
            where (:objectId is null or object_id = :objectId)
            order by name asc, code asc
            """,
            params().addValue("objectId", objectId),
            (rs, rowNum) -> new MetricDto(
                uuid(rs, "id"),
                uuid(rs, "object_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("formula_type"),
                rs.getString("formula_json"),
                rs.getString("format"),
                rs.getString("unit"),
                rs.getString("status")
            )
        );
    }

    public MetricDto createMetric(MetricRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_metric
                (id, object_id, code, name, formula_type, formula_json, format, unit, status, created_date, last_modified_date)
            values
                (:id, :objectId, :code, :name, :formulaType, :formulaJson, :format, :unit, :status, :now, :now)
            """,
            metricParams(id, request)
        );
        return getMetric(id);
    }

    public MetricDto updateMetric(UUID id, MetricRequest request) {
        jdbc.update(
            """
            update semantic_metric
            set object_id = :objectId,
                code = :code,
                name = :name,
                formula_type = :formulaType,
                formula_json = :formulaJson,
                format = :format,
                unit = :unit,
                status = :status,
                last_modified_date = :now
            where id = :id
            """,
            metricParams(id, request)
        );
        return getMetric(id);
    }

    @Transactional(readOnly = true)
    public List<ModelDto> listModels(String type) {
        return jdbc.query(
            """
            select id, object_id, type, name, table_name, description, grain, materialization, refresh_cycle, status,
                   review_status, submitted_by, submitted_at, reviewed_by, reviewed_at, review_comment
            from semantic_model
            where (:type is null or upper(type) = upper(:type))
            order by last_modified_date desc nulls last, name asc
            """,
            params().addValue("type", trimToNull(type)),
            (rs, rowNum) -> new ModelDto(
                uuid(rs, "id"),
                uuid(rs, "object_id"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("table_name"),
                rs.getString("description"),
                rs.getString("grain"),
                rs.getString("materialization"),
                rs.getString("refresh_cycle"),
                rs.getString("status"),
                defaultValue(rs.getString("review_status"), "DRAFT"),
                rs.getString("submitted_by"),
                instant(rs, "submitted_at"),
                rs.getString("reviewed_by"),
                instant(rs, "reviewed_at"),
                rs.getString("review_comment")
            )
        );
    }

    public ModelDto createModel(ModelRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_model
                (id, object_id, type, name, table_name, description, grain, materialization, refresh_cycle, status, created_date, last_modified_date)
            values
                (:id, :objectId, :type, :name, :tableName, :description, :grain, :materialization, :refreshCycle, :status, :now, :now)
            """,
            modelParams(id, request)
        );
        return getModel(id);
    }

    public ModelDto submitModelReview(UUID modelId, ReviewActionRequest request, String actor) {
        ModelDto model = getModel(modelId);
        validateModelForReview(model);
        updateModelReview(modelId, "IN_REVIEW", "IN_REVIEW", actor, null, trimToNull(request == null ? null : request.comment()));
        appendReviewLog(modelId, "SUBMIT", actor, request == null ? null : request.comment());
        return getModel(modelId);
    }

    public ModelDto approveModelReview(UUID modelId, ReviewActionRequest request, String actor) {
        ModelDto model = getModel(modelId);
        String reviewStatus = defaultValue(model.reviewStatus(), "DRAFT");
        if (!"IN_REVIEW".equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("只有待审核模型可以审核通过");
        }
        updateModelReview(modelId, "APPROVED", "APPROVED", null, actor, trimToNull(request == null ? null : request.comment()));
        appendReviewLog(modelId, "APPROVE", actor, request == null ? null : request.comment());
        return getModel(modelId);
    }

    public ModelDto rejectModelReview(UUID modelId, ReviewActionRequest request, String actor) {
        ModelDto model = getModel(modelId);
        String reviewStatus = defaultValue(model.reviewStatus(), "DRAFT");
        if (!"IN_REVIEW".equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("只有待审核模型可以驳回");
        }
        String comment = required(request == null ? null : request.comment(), "驳回原因");
        updateModelReview(modelId, "REJECTED", "REJECTED", null, actor, comment);
        appendReviewLog(modelId, "REJECT", actor, comment);
        return getModel(modelId);
    }

    @Transactional(readOnly = true)
    public List<ModelReviewLogDto> listModelReviewLogs(UUID modelId) {
        getModel(modelId);
        return jdbc.query(
            """
            select id, model_id, action, actor, comment, created_date
            from semantic_model_review_log
            where model_id = :modelId
            order by created_date desc nulls last
            """,
            params().addValue("modelId", modelId),
            (rs, rowNum) -> new ModelReviewLogDto(
                uuid(rs, "id"),
                uuid(rs, "model_id"),
                rs.getString("action"),
                rs.getString("actor"),
                rs.getString("comment"),
                instant(rs, "created_date")
            )
        );
    }

    public ModelDto updateModel(UUID id, ModelRequest request) {
        jdbc.update(
            """
            update semantic_model
            set object_id = :objectId,
                type = :type,
                name = :name,
                table_name = :tableName,
                description = :description,
                grain = :grain,
                materialization = :materialization,
                refresh_cycle = :refreshCycle,
                status = :status,
                last_modified_date = :now
            where id = :id
            """,
            modelParams(id, request)
        );
        return getModel(id);
    }

    @Transactional(readOnly = true)
    public ModelBindingDto getModelBindings(UUID modelId) {
        getModel(modelId);
        List<UUID> dimensionIds = jdbc.query(
            """
            select dimension_id
            from semantic_model_dimension
            where model_id = :modelId
            order by sort_order asc
            """,
            params().addValue("modelId", modelId),
            (rs, rowNum) -> uuid(rs, "dimension_id")
        );
        List<UUID> metricIds = jdbc.query(
            """
            select metric_id
            from semantic_model_metric
            where model_id = :modelId
            order by sort_order asc
            """,
            params().addValue("modelId", modelId),
            (rs, rowNum) -> uuid(rs, "metric_id")
        );
        return new ModelBindingDto(modelId, dimensionIds, metricIds);
    }

    public ModelBindingDto saveModelBindings(UUID modelId, ModelBindingRequest request) {
        getModel(modelId);
        jdbc.update("delete from semantic_model_dimension where model_id = :modelId", params().addValue("modelId", modelId));
        jdbc.update("delete from semantic_model_metric where model_id = :modelId", params().addValue("modelId", modelId));
        List<UUID> dimensionIds = request == null || request.dimensionIds() == null ? List.of() : request.dimensionIds();
        for (int i = 0; i < dimensionIds.size(); i++) {
            UUID dimensionId = dimensionIds.get(i);
            if (dimensionId == null) continue;
            getDimension(dimensionId);
            jdbc.update(
                """
                insert into semantic_model_dimension
                    (id, model_id, dimension_id, sort_order, created_date, last_modified_date)
                values
                    (:id, :modelId, :dimensionId, :sortOrder, :now, :now)
                """,
                params()
                    .addValue("id", UUID.randomUUID())
                    .addValue("modelId", modelId)
                    .addValue("dimensionId", dimensionId)
                    .addValue("sortOrder", i)
            );
        }
        List<UUID> metricIds = request == null || request.metricIds() == null ? List.of() : request.metricIds();
        for (int i = 0; i < metricIds.size(); i++) {
            UUID metricId = metricIds.get(i);
            if (metricId == null) continue;
            getMetric(metricId);
            jdbc.update(
                """
                insert into semantic_model_metric
                    (id, model_id, metric_id, sort_order, created_date, last_modified_date)
                values
                    (:id, :modelId, :metricId, :sortOrder, :now, :now)
                """,
                params()
                    .addValue("id", UUID.randomUUID())
                    .addValue("modelId", modelId)
                    .addValue("metricId", metricId)
                    .addValue("sortOrder", i)
            );
        }
        return getModelBindings(modelId);
    }

    @Transactional(readOnly = true)
    public List<GeneratedArtifactDto> listGeneratedArtifacts(UUID modelId) {
        return jdbc.query(
            """
            select id, model_id, artifact_type, path, content, status
            from semantic_generated_artifact
            where (:modelId is null or model_id = :modelId)
            order by last_modified_date desc nulls last, path asc
            """,
            params().addValue("modelId", modelId),
            (rs, rowNum) -> new GeneratedArtifactDto(
                uuid(rs, "id"),
                uuid(rs, "model_id"),
                rs.getString("artifact_type"),
                rs.getString("path"),
                rs.getString("content"),
                rs.getString("status")
            )
        );
    }

    public GenerateArtifactsResult generateArtifacts(UUID modelId) {
        ModelDto model = getModel(modelId);
        BusinessObjectDto object = model.objectId() == null ? null : getBusinessObject(model.objectId());
        if (object == null) {
            throw new IllegalArgumentException("模型未绑定业务对象，无法生成 SQL/dbt");
        }
        if (!StringUtils.hasText(object.mainTable())) {
            throw new IllegalArgumentException("业务对象未配置主表，无法生成 SQL/dbt");
        }
        String tableName = defaultValue(model.tableName(), safeIdentifier(model.name()));
        String modelName = safeIdentifier(tableName);
        String type = defaultValue(model.type(), "DWS").toLowerCase(Locale.ROOT);
        String basePath = "models/" + type + "/semantic/" + modelName;

        ModelBindingDto bindings = getModelBindings(modelId);
        List<DimensionDto> dimensions = selectBoundDimensions(model.objectId(), bindings.dimensionIds());
        List<MetricDto> metrics = selectBoundMetrics(model.objectId(), bindings.metricIds());
        String sql = buildModelSql(model, object, dimensions, metrics);
        String schema = buildSchemaYaml(modelName, model, dimensions, metrics);
        String doc = buildMetricDoc(model, object, dimensions, metrics);

        jdbc.update("delete from semantic_generated_artifact where model_id = :modelId", params().addValue("modelId", modelId));
        List<GeneratedArtifactDto> artifacts = new ArrayList<>();
        artifacts.add(createGeneratedArtifact(modelId, "DBT_SQL", basePath + ".sql", sql, "GENERATED"));
        artifacts.add(createGeneratedArtifact(modelId, "DBT_SCHEMA_YML", "models/" + type + "/semantic/schema.yml", schema, "GENERATED"));
        artifacts.add(createGeneratedArtifact(modelId, "METRIC_DOC", "docs/semantic/" + modelName + ".md", doc, "GENERATED"));
        return new GenerateArtifactsResult(model, artifacts);
    }

    @Transactional(readOnly = true)
    public ModelPreviewResult previewModel(UUID modelId, Integer limit) {
        int rowLimit = Math.max(1, Math.min(limit == null ? 100 : limit, 200));
        ModelDto model = getModel(modelId);
        BusinessObjectDto object = model.objectId() == null ? null : getBusinessObject(model.objectId());
        if (object == null) {
            throw new IllegalArgumentException("模型未绑定业务对象，无法预览数据");
        }
        if (!StringUtils.hasText(object.mainTable())) {
            throw new IllegalArgumentException("业务对象未配置主表，无法预览数据");
        }
        ModelBindingDto bindings = getModelBindings(modelId);
        List<DimensionDto> dimensions = selectBoundDimensions(model.objectId(), bindings.dimensionIds());
        List<MetricDto> metrics = selectBoundMetrics(model.objectId(), bindings.metricIds());
        String sql = appendLimit(buildExecutableModelSql(model, object, dimensions, metrics), rowLimit);
        try {
            Map<String, Object> payload = queryGateway.execute(sql);
            List<String> headers = extractPreviewHeaders(payload);
            List<Map<String, Object>> rows = extractPreviewRows(payload.get("rows"), rowLimit);
            long rowCount = numberOrDefault(payload.get("rowCount"), rows.size());
            long durationMs = numberOrDefault(payload.get("durationMs"), numberOrDefault(payload.get("queryMillis"), 0L));
            return new ModelPreviewResult(true, sql, headers, rows, rowCount, durationMs, null);
        } catch (Exception ex) {
            return new ModelPreviewResult(false, sql, List.of(), List.of(), 0L, 0L, ex.getMessage());
        }
    }

    public PublishArtifactsResult publishArtifacts(UUID modelId) {
        ModelDto model = getModel(modelId);
        String reviewStatus = defaultValue(model.reviewStatus(), "DRAFT");
        if (!"APPROVED".equalsIgnoreCase(reviewStatus) && !"PUBLISHED".equalsIgnoreCase(defaultValue(model.status(), ""))) {
            throw new IllegalArgumentException("模型未审核通过，不能发布 dbt");
        }
        List<GeneratedArtifactDto> artifacts = listGeneratedArtifacts(modelId);
        if (artifacts.isEmpty()) {
            artifacts = generateArtifacts(modelId).artifacts();
        }
        List<String> published = new ArrayList<>();
        for (GeneratedArtifactDto artifact : artifacts) {
            if (!StringUtils.hasText(artifact.path()) || !StringUtils.hasText(artifact.content())) continue;
            upsertDbtFile(artifact.path(), artifact.content());
            jdbc.update(
                "update semantic_generated_artifact set status = 'PUBLISHED', last_modified_date = :now where id = :id",
                params().addValue("id", artifact.id())
            );
            published.add(artifact.path());
        }
        jdbc.update(
            """
            update semantic_model
            set status = 'PUBLISHED',
                review_status = 'APPROVED',
                last_modified_date = :now
            where id = :id
            """,
            params().addValue("id", modelId)
        );
        return new PublishArtifactsResult(modelId, published);
    }

    public RegisterBiDatasetResult registerBiDataset(UUID modelId) {
        ModelDto model = getModel(modelId);
        BusinessObjectDto object = model.objectId() == null ? null : getBusinessObject(model.objectId());
        if (object == null) {
            throw new IllegalArgumentException("模型未绑定业务对象，无法注册 BI 数据集");
        }
        String tableName = safeIdentifier(defaultValue(model.tableName(), model.name()));
        String datasetSql = "select * from " + tableName;
        UUID existingDatasetId = findArtifactLinkedId(modelId, "BI_DATASET");

        QueryDatasetAsset asset = existingDatasetId == null
            ? new QueryDatasetAsset()
            : queryDatasetRepository.findById(existingDatasetId).orElseGet(QueryDatasetAsset::new);
        asset.setName(defaultValue(model.name(), tableName));
        asset.setDescription(defaultValue(model.description(), "由指标语义建模中心注册的 BI 数据集"));
        asset.setRefreshStrategy(defaultValue(model.refreshCycle(), "MANUAL").toUpperCase(Locale.ROOT));
        asset.setSqlText(datasetSql);
        asset.setStatus("PUBLISHED");
        asset.setPublishedVersion(1);
        asset.setEnabled(Boolean.TRUE);
        asset = queryDatasetRepository.save(asset);

        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(queryDatasetVersionRepository.findMaxVersionNo(asset.getId()) + 1);
        version.setStatus("PUBLISHED");
        version.setSqlText(datasetSql);
        version.setChangeSummary("指标语义模型注册 BI 数据集");
        version.setPublishedAt(Instant.now());
        queryDatasetVersionRepository.save(version);
        asset.setPublishedVersion(version.getVersionNo());
        queryDatasetRepository.save(asset);

        String content = "{\"datasetId\":\"" + asset.getId() + "\",\"datasetName\":\"" + escapeJson(asset.getName()) + "\",\"sql\":\"" + escapeJson(datasetSql) + "\"}";
        upsertGeneratedArtifact(modelId, "BI_DATASET", "query-dataset://" + asset.getId(), content, "REGISTERED");
        return new RegisterBiDatasetResult(modelId, asset.getId(), asset.getName(), version.getVersionNo());
    }

    public RegisterLineageResult registerLineage(UUID modelId) {
        ModelDto model = getModel(modelId);
        BusinessObjectDto object = model.objectId() == null ? null : getBusinessObject(model.objectId());
        if (object == null) {
            throw new IllegalArgumentException("模型未绑定业务对象，无法写入血缘");
        }
        String upstreamTable = safeIdentifier(required(object.mainTable(), "mainTable"));
        String downstreamTable = safeIdentifier(defaultValue(model.tableName(), model.name()));
        CatalogDataset upstream = findOrCreateCatalogDataset(upstreamTable, "DWD", "语义模型上游明细模型");
        CatalogDataset downstream = findOrCreateCatalogDataset(downstreamTable, defaultValue(model.type(), "DWS").toUpperCase(Locale.ROOT), "语义模型生成目标模型");

        CatalogDatasetLineage lineage = catalogLineageRepository
            .findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(upstream.getId(), downstream.getId(), "TRANSFORM")
            .orElseGet(CatalogDatasetLineage::new);
        lineage.setUpstreamDatasetId(upstream.getId());
        lineage.setDownstreamDatasetId(downstream.getId());
        lineage.setRelationType("TRANSFORM");
        lineage.setUpstreamAssetType("DATASET");
        lineage.setDownstreamAssetType("DATASET");
        lineage.setDirection("UPSTREAM_TO_DOWNSTREAM");
        lineage.setProjectName("semantic-modeling");
        lineage.setNotes("指标语义建模中心生成血缘: " + object.name() + " -> " + model.name());
        lineage.setVerificationStatus("DECLARED");
        lineage.setLastObservedAt(Instant.now());
        if (lineage.getValidFrom() == null) {
            lineage.setValidFrom(Instant.now());
        }
        lineage = catalogLineageRepository.save(lineage);

        String content = "{\"lineageId\":\"" + lineage.getId() + "\",\"upstreamDatasetId\":\"" + upstream.getId() + "\",\"downstreamDatasetId\":\"" + downstream.getId() + "\"}";
        upsertGeneratedArtifact(modelId, "LINEAGE", "catalog-lineage://" + lineage.getId(), content, "REGISTERED");
        return new RegisterLineageResult(modelId, lineage.getId(), upstream.getId(), downstream.getId());
    }

    public ModelRunDto triggerModelRun(UUID modelId, ModelRunRequest request, String actor, String activeDept) {
        ModelDto model = getModel(modelId);
        String modelStatus = defaultValue(model.status(), "");
        if (!"PUBLISHED".equalsIgnoreCase(modelStatus) && !"RUN_FAILED".equalsIgnoreCase(modelStatus)) {
            throw new IllegalArgumentException("模型未发布，不能触发运行");
        }
        String selector = safeIdentifier(defaultValue(model.tableName(), model.name()));
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("semantic_model_id", model.id().toString());
        vars.put("semantic_model_name", model.name());
        if (request != null && request.vars() != null) {
            vars.putAll(request.vars());
        }
        DbtReleaseSubmissionService.DbtReleaseSubmitRequest submitRequest = new DbtReleaseSubmissionService.DbtReleaseSubmitRequest(
            selector,
            trimToNull(request == null ? null : request.dagSelector()),
            trimToNull(request == null ? null : request.target()),
            vars,
            null,
            null,
            false,
            true
        );
        DbtReleaseSubmissionService.DbtReleaseSubmitResult result = dbtReleaseSubmissionService.submit(submitRequest, activeDept);
        String status = result.blocking() ? "BLOCKED" : defaultValue(result.status(), "SUBMITTED");
        String message = buildRunMessage(result);
        UUID runId = insertModelRun(
            modelId,
            defaultValue(request == null ? null : request.runType(), "DBT_RUN"),
            result.selector(),
            result.dagId(),
            result.dagRunId(),
            status,
            actor,
            message,
            toJson(Map.of(
                "selector", defaultValue(result.selector(), selector),
                "dagId", defaultValue(result.dagId(), ""),
                "dagRunId", defaultValue(result.dagRunId(), ""),
                "status", status,
                "blockers", result.blockers() == null ? List.of() : result.blockers(),
                "warnings", result.warnings() == null ? List.of() : result.warnings()
            ))
        );
        if (!result.blocking()) {
            jdbc.update(
                """
                update semantic_model
                set status = 'RUNNING',
                    last_modified_date = :now
                where id = :id
                """,
                params().addValue("id", modelId)
            );
        }
        return getModelRun(runId);
    }

    @Transactional(readOnly = true)
    public List<ModelRunDto> listModelRuns(UUID modelId) {
        getModel(modelId);
        return jdbc.query(
            """
            select id, model_id, run_type, selector, dag_id, external_run_id, status, triggered_by,
                   started_at, finished_at, duration_ms, message, payload_json, created_date
            from semantic_model_run
            where model_id = :modelId
            order by started_at desc nulls last, created_date desc nulls last
            """,
            params().addValue("modelId", modelId),
            (rs, rowNum) -> mapModelRun(rs)
        );
    }

    public ModelRunDto updateModelRun(UUID modelId, UUID runId, ModelRunStatusRequest request) {
        getModel(modelId);
        ModelRunDto current = getModelRun(runId);
        if (!modelId.equals(current.modelId())) {
            throw new IllegalArgumentException("运行记录不属于当前模型");
        }
        String status = defaultValue(request == null ? null : request.status(), current.status()).toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        Instant finishedAt = isTerminalRunStatus(status) ? now : current.finishedAt();
        Long durationMs = request == null || request.durationMs() == null ? current.durationMs() : request.durationMs();
        if (durationMs == null && current.startedAt() != null && finishedAt != null) {
            durationMs = Math.max(0L, java.time.Duration.between(current.startedAt(), finishedAt).toMillis());
        }
        jdbc.update(
            """
            update semantic_model_run
            set status = :status,
                external_run_id = coalesce(:externalRunId, external_run_id),
                finished_at = :finishedAt,
                duration_ms = :durationMs,
                message = coalesce(:message, message),
                payload_json = coalesce(:payloadJson, payload_json),
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", runId)
                .addValue("status", status)
                .addValue("externalRunId", trimToNull(request == null ? null : request.externalRunId()))
                .addValue("finishedAt", finishedAt)
                .addValue("durationMs", durationMs)
                .addValue("message", trimToNull(request == null ? null : request.message()))
                .addValue("payloadJson", request == null ? null : toJson(request.payload()))
        );
        updateModelStatusFromRun(modelId, status, request == null ? null : request.message());
        return getModelRun(runId);
    }

    private UUID insertModelRun(
        UUID modelId,
        String runType,
        String selector,
        String dagId,
        String externalRunId,
        String status,
        String actor,
        String message,
        String payloadJson
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_model_run
                (id, model_id, run_type, selector, dag_id, external_run_id, status, triggered_by,
                 started_at, message, payload_json, created_date, last_modified_date)
            values
                (:id, :modelId, :runType, :selector, :dagId, :externalRunId, :status, :triggeredBy,
                 :now, :message, :payloadJson, :now, :now)
            """,
            params()
                .addValue("id", id)
                .addValue("modelId", modelId)
                .addValue("runType", runType)
                .addValue("selector", trimToNull(selector))
                .addValue("dagId", trimToNull(dagId))
                .addValue("externalRunId", trimToNull(externalRunId))
                .addValue("status", status)
                .addValue("triggeredBy", trimToNull(actor))
                .addValue("message", trimToNull(message))
                .addValue("payloadJson", trimToNull(payloadJson))
        );
        return id;
    }

    private ModelRunDto getModelRun(UUID id) {
        return listOne(
            """
            select id, model_id, run_type, selector, dag_id, external_run_id, status, triggered_by,
                   started_at, finished_at, duration_ms, message, payload_json, created_date
            from semantic_model_run
            where id = :id
            """,
            params().addValue("id", id),
            (rs, rowNum) -> mapModelRun(rs)
        );
    }

    private ModelRunDto mapModelRun(ResultSet rs) throws SQLException {
        return new ModelRunDto(
            uuid(rs, "id"),
            uuid(rs, "model_id"),
            rs.getString("run_type"),
            rs.getString("selector"),
            rs.getString("dag_id"),
            rs.getString("external_run_id"),
            rs.getString("status"),
            rs.getString("triggered_by"),
            instant(rs, "started_at"),
            instant(rs, "finished_at"),
            longValue(rs, "duration_ms"),
            rs.getString("message"),
            rs.getString("payload_json"),
            instant(rs, "created_date")
        );
    }

    private void updateModelStatusFromRun(UUID modelId, String runStatus, String message) {
        String modelStatus = switch (defaultValue(runStatus, "").toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED" -> "PUBLISHED";
            case "FAILED", "ERROR", "CANCELED", "CANCELLED" -> "RUN_FAILED";
            case "RUNNING", "QUEUED", "SUBMITTED" -> "RUNNING";
            default -> null;
        };
        if (!StringUtils.hasText(modelStatus)) return;
        jdbc.update(
            """
            update semantic_model
            set status = :status,
                review_comment = coalesce(:message, review_comment),
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", modelId)
                .addValue("status", modelStatus)
                .addValue("message", trimToNull(message))
        );
    }

    private boolean isTerminalRunStatus(String status) {
        String normalized = defaultValue(status, "").toUpperCase(Locale.ROOT);
        return List.of("SUCCESS", "SUCCEEDED", "FAILED", "ERROR", "CANCELED", "CANCELLED", "BLOCKED").contains(normalized);
    }

    private String buildRunMessage(DbtReleaseSubmissionService.DbtReleaseSubmitResult result) {
        if (result == null) return null;
        if (result.blockers() != null && !result.blockers().isEmpty()) {
            return String.join("；", result.blockers());
        }
        if (result.warnings() != null && !result.warnings().isEmpty()) {
            return String.join("；", result.warnings());
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败: " + ex.getMessage());
        }
    }

    private SubjectDomainDto getSubjectDomain(UUID id) {
        return listOne(
            """
            select id, code, name, description, status, owner_dept,
                   governance_domain_id, governance_domain_code, governance_domain_name
            from semantic_subject_domain
            where id = :id
            """,
            params().addValue("id", id),
            (rs, rowNum) -> new SubjectDomainDto(
                uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("owner_dept"),
                uuid(rs, "governance_domain_id"),
                rs.getString("governance_domain_code"),
                rs.getString("governance_domain_name")
            )
        );
    }

    private BusinessObjectDto getBusinessObject(UUID id) {
        return listOne(
            "select id, domain_id, code, name, description, primary_key, main_table, status, owner_dept from semantic_business_object where id = :id",
            params().addValue("id", id),
            (rs, rowNum) -> new BusinessObjectDto(uuid(rs, "id"), uuid(rs, "domain_id"), rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("primary_key"), rs.getString("main_table"), rs.getString("status"), rs.getString("owner_dept"))
        );
    }

    private DimensionDto getDimension(UUID id) {
        return listOne(
            "select id, object_id, code, name, field_name, data_type, semantic_type, status from semantic_dimension where id = :id",
            params().addValue("id", id),
            (rs, rowNum) -> new DimensionDto(uuid(rs, "id"), uuid(rs, "object_id"), rs.getString("code"), rs.getString("name"), rs.getString("field_name"), rs.getString("data_type"), rs.getString("semantic_type"), rs.getString("status"))
        );
    }

    private MetricDto getMetric(UUID id) {
        return listOne(
            "select id, object_id, code, name, formula_type, formula_json, format, unit, status from semantic_metric where id = :id",
            params().addValue("id", id),
            (rs, rowNum) -> new MetricDto(uuid(rs, "id"), uuid(rs, "object_id"), rs.getString("code"), rs.getString("name"), rs.getString("formula_type"), rs.getString("formula_json"), rs.getString("format"), rs.getString("unit"), rs.getString("status"))
        );
    }

    private ModelDto getModel(UUID id) {
        return listOne(
            """
            select id, object_id, type, name, table_name, description, grain, materialization, refresh_cycle, status,
                   review_status, submitted_by, submitted_at, reviewed_by, reviewed_at, review_comment
            from semantic_model
            where id = :id
            """,
            params().addValue("id", id),
            (rs, rowNum) -> new ModelDto(
                uuid(rs, "id"),
                uuid(rs, "object_id"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("table_name"),
                rs.getString("description"),
                rs.getString("grain"),
                rs.getString("materialization"),
                rs.getString("refresh_cycle"),
                rs.getString("status"),
                defaultValue(rs.getString("review_status"), "DRAFT"),
                rs.getString("submitted_by"),
                instant(rs, "submitted_at"),
                rs.getString("reviewed_by"),
                instant(rs, "reviewed_at"),
                rs.getString("review_comment")
            )
        );
    }

    private GeneratedArtifactDto createGeneratedArtifact(UUID modelId, String artifactType, String path, String content, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into semantic_generated_artifact
                (id, model_id, artifact_type, path, content, status, created_date, last_modified_date)
            values
                (:id, :modelId, :artifactType, :path, :content, :status, :now, :now)
            """,
            params()
                .addValue("id", id)
                .addValue("modelId", modelId)
                .addValue("artifactType", artifactType)
                .addValue("path", path)
                .addValue("content", content)
                .addValue("status", status)
        );
        return new GeneratedArtifactDto(id, modelId, artifactType, path, content, status);
    }

    private GeneratedArtifactDto upsertGeneratedArtifact(UUID modelId, String artifactType, String path, String content, String status) {
        UUID existingId = jdbc.query(
            """
            select id
            from semantic_generated_artifact
            where model_id = :modelId and artifact_type = :artifactType
            order by last_modified_date desc nulls last
            limit 1
            """,
            params().addValue("modelId", modelId).addValue("artifactType", artifactType),
            (rs, rowNum) -> uuid(rs, "id")
        ).stream().findFirst().orElse(null);
        if (existingId == null) {
            return createGeneratedArtifact(modelId, artifactType, path, content, status);
        }
        jdbc.update(
            """
            update semantic_generated_artifact
            set path = :path,
                content = :content,
                status = :status,
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", existingId)
                .addValue("path", path)
                .addValue("content", content)
                .addValue("status", status)
        );
        return new GeneratedArtifactDto(existingId, modelId, artifactType, path, content, status);
    }

    private List<DimensionDto> selectBoundDimensions(UUID objectId, List<UUID> dimensionIds) {
        List<DimensionDto> all = listDimensions(objectId);
        if (dimensionIds == null || dimensionIds.isEmpty()) return all;
        List<DimensionDto> selected = new ArrayList<>();
        for (UUID id : dimensionIds) {
            all.stream().filter(item -> id.equals(item.id())).findFirst().ifPresent(selected::add);
        }
        return selected;
    }

    private List<MetricDto> selectBoundMetrics(UUID objectId, List<UUID> metricIds) {
        List<MetricDto> all = listMetrics(objectId);
        if (metricIds == null || metricIds.isEmpty()) return all;
        List<MetricDto> selected = new ArrayList<>();
        for (UUID id : metricIds) {
            all.stream().filter(item -> id.equals(item.id())).findFirst().ifPresent(selected::add);
        }
        return selected;
    }

    private UUID findArtifactLinkedId(UUID modelId, String artifactType) {
        return jdbc.query(
            """
            select content
            from semantic_generated_artifact
            where model_id = :modelId and artifact_type = :artifactType
            order by last_modified_date desc nulls last
            limit 1
            """,
            params().addValue("modelId", modelId).addValue("artifactType", artifactType),
            (rs, rowNum) -> rs.getString("content")
        ).stream().findFirst().map(content -> {
            try {
                JsonNode node = objectMapper.readTree(content);
                String id = defaultValue(readText(node, "datasetId"), readText(node, "lineageId"));
                return StringUtils.hasText(id) ? UUID.fromString(id) : null;
            } catch (Exception ex) {
                return null;
            }
        }).orElse(null);
    }

    private CatalogDataset findOrCreateCatalogDataset(String tableName, String layer, String description) {
        CatalogDataset dataset = catalogDatasetRepository
            .findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("semantic", tableName)
            .stream()
            .findFirst()
            .orElseGet(CatalogDataset::new);
        dataset.setName(tableName);
        dataset.setType("hive");
        dataset.setHiveDatabase("semantic");
        dataset.setHiveTable(tableName);
        dataset.setWarehouseLayer(layer);
        dataset.setDescription(description);
        dataset.setClassification("INTERNAL");
        dataset.setExposedBy("VIEW");
        dataset.setEnabled(Boolean.TRUE);
        dataset.setLifecycleStatus("ACTIVE");
        return catalogDatasetRepository.save(dataset);
    }

    private String buildModelSql(ModelDto model, BusinessObjectDto object, List<DimensionDto> dimensions, List<MetricDto> metrics) {
        return buildSelectSql(model, dimensions, metrics, buildDbtFromClause(object), true);
    }

    private String buildExecutableModelSql(ModelDto model, BusinessObjectDto object, List<DimensionDto> dimensions, List<MetricDto> metrics) {
        return buildSelectSql(model, dimensions, metrics, buildExecutableFromClause(object), false);
    }

    private String buildSelectSql(ModelDto model, List<DimensionDto> dimensions, List<MetricDto> metrics, String fromClause, boolean dbtModel) {
        List<String> selectItems = new ArrayList<>();
        List<String> groupByItems = new ArrayList<>();
        for (DimensionDto dimension : dimensions) {
            String field = safeField(dimension.fieldName());
            if (!StringUtils.hasText(field)) continue;
            String alias = safeIdentifier(defaultValue(dimension.code(), dimension.name()));
            selectItems.add("    " + field + " as " + alias);
            groupByItems.add(field);
        }
        for (MetricDto metric : metrics) {
            String expression = buildMetricExpression(metric);
            String alias = safeIdentifier(defaultValue(metric.code(), metric.name()));
            selectItems.add("    " + expression + " as " + alias);
        }
        if (selectItems.isEmpty()) {
            selectItems.add("    *");
        }

        StringBuilder sql = new StringBuilder();
        if (dbtModel) {
            sql.append("{{ config(materialized='")
                .append(defaultValue(model.materialization(), "table"))
                .append("') }}\n\n");
        }
        sql.append("select\n");
        sql.append(String.join(",\n", selectItems));
        sql.append("\nfrom ").append(fromClause).append("\n");
        if (!groupByItems.isEmpty() && hasAggregateMetric(metrics)) {
            sql.append("group by\n");
            for (int i = 0; i < groupByItems.size(); i++) {
                sql.append("    ").append(groupByItems.get(i));
                if (i < groupByItems.size() - 1) sql.append(",");
                sql.append("\n");
            }
        }
        return sql.toString();
    }

    private String buildDbtFromClause(BusinessObjectDto object) {
        List<ObjectTableMappingDto> mappings = listObjectTableMappings(object.id());
        if (mappings.isEmpty()) {
            return "{{ ref('" + safeIdentifier(object.mainTable()) + "') }}";
        }
        String primary = mappings.stream()
            .filter(item -> "PRIMARY".equalsIgnoreCase(defaultValue(item.tableRole(), "")))
            .findFirst()
            .map(ObjectTableMappingDto::tableName)
            .orElse(object.mainTable());
        StringBuilder from = new StringBuilder("{{ ref('").append(safeIdentifier(primary)).append("') }}");
        appendJoinClauses(from, mappings, true);
        return from.toString();
    }

    private String buildExecutableFromClause(BusinessObjectDto object) {
        List<ObjectTableMappingDto> mappings = listObjectTableMappings(object.id());
        if (mappings.isEmpty()) {
            return safeTableRef(object.mainTable());
        }
        String primary = mappings.stream()
            .filter(item -> "PRIMARY".equalsIgnoreCase(defaultValue(item.tableRole(), "")))
            .findFirst()
            .map(ObjectTableMappingDto::tableName)
            .orElse(object.mainTable());
        StringBuilder from = new StringBuilder(safeTableRef(primary));
        appendJoinClauses(from, mappings, false);
        return from.toString();
    }

    private void appendJoinClauses(StringBuilder from, List<ObjectTableMappingDto> mappings, boolean dbtRef) {
        for (ObjectTableMappingDto mapping : mappings) {
            if ("PRIMARY".equalsIgnoreCase(defaultValue(mapping.tableRole(), ""))) continue;
            if (!StringUtils.hasText(mapping.tableName()) || !StringUtils.hasText(mapping.joinExpression())) continue;
            String joinType = joinKeyword(mapping.tableRole());
            String tableRef = dbtRef
                ? "{{ ref('" + safeIdentifier(mapping.tableName()) + "') }}"
                : safeTableRef(mapping.tableName());
            from.append("\n").append(joinType).append(" ").append(tableRef)
                .append(" on ").append(safeJoinExpression(mapping.joinExpression()));
        }
    }

    private String buildSchemaYaml(String modelName, ModelDto model, List<DimensionDto> dimensions, List<MetricDto> metrics) {
        StringBuilder yml = new StringBuilder();
        yml.append("version: 2\n\nmodels:\n");
        yml.append("  - name: ").append(modelName).append("\n");
        yml.append("    description: \"").append(escapeYaml(defaultValue(model.description(), model.name()))).append("\"\n");
        yml.append("    columns:\n");
        for (DimensionDto dimension : dimensions) {
            appendYamlColumn(yml, safeIdentifier(defaultValue(dimension.code(), dimension.name())), dimension.name(), "dimension");
        }
        for (MetricDto metric : metrics) {
            appendYamlColumn(yml, safeIdentifier(defaultValue(metric.code(), metric.name())), metric.name(), "metric");
        }
        if (dimensions.isEmpty() && metrics.isEmpty()) {
            yml.append("      []\n");
        }
        return yml.toString();
    }

    private void appendYamlColumn(StringBuilder yml, String name, String description, String metaType) {
        yml.append("      - name: ").append(name).append("\n");
        yml.append("        description: \"").append(escapeYaml(defaultValue(description, name))).append("\"\n");
        yml.append("        meta:\n");
        yml.append("          semantic_type: ").append(metaType).append("\n");
    }

    private String buildMetricDoc(ModelDto model, BusinessObjectDto object, List<DimensionDto> dimensions, List<MetricDto> metrics) {
        StringBuilder doc = new StringBuilder();
        doc.append("# ").append(model.name()).append("\n\n");
        doc.append("- 类型: ").append(defaultValue(model.type(), "-")).append("\n");
        doc.append("- 业务对象: ").append(object.name()).append("\n");
        doc.append("- 来源主表: ").append(object.mainTable()).append("\n");
        doc.append("- 目标表: ").append(defaultValue(model.tableName(), safeIdentifier(model.name()))).append("\n\n");
        doc.append("## 维度\n\n");
        if (dimensions.isEmpty()) {
            doc.append("暂无维度。\n\n");
        } else {
            for (DimensionDto dimension : dimensions) {
                doc.append("- ").append(dimension.name()).append(": ").append(defaultValue(dimension.fieldName(), "-")).append("\n");
            }
            doc.append("\n");
        }
        doc.append("## 指标\n\n");
        if (metrics.isEmpty()) {
            doc.append("暂无指标。\n");
        } else {
            for (MetricDto metric : metrics) {
                doc.append("- ").append(metric.name()).append(": ").append(defaultValue(metric.formulaType(), "-"));
                if (StringUtils.hasText(metric.unit())) doc.append("，单位 ").append(metric.unit());
                doc.append("\n");
            }
        }
        return doc.toString();
    }

    private String buildMetricExpression(MetricDto metric) {
        String type = defaultValue(metric.formulaType(), "sum").toLowerCase(Locale.ROOT);
        JsonNode formula = parseFormula(metric.formulaJson());
        return switch (type) {
            case "count" -> "count(" + safeField(defaultValue(readText(formula, "field"), "*")) + ")";
            case "count_distinct" -> "count(distinct " + safeField(required(readText(formula, "field"), "metric field")) + ")";
            case "avg" -> "avg(" + safeField(required(readText(formula, "field"), "metric field")) + ")";
            case "max" -> "max(" + safeField(required(readText(formula, "field"), "metric field")) + ")";
            case "min" -> "min(" + safeField(required(readText(formula, "field"), "metric field")) + ")";
            case "count_if" -> "sum(case when " + conditionSql(formula) + " then 1 else 0 end)";
            case "sum_if" -> "sum(case when " + conditionSql(formula) + " then " + safeField(required(readText(formula, "field"), "metric field")) + " else 0 end)";
            case "ratio" -> ratioSql(formula);
            default -> "sum(" + safeField(required(readText(formula, "field"), "metric field")) + ")";
        };
    }

    private String ratioSql(JsonNode formula) {
        JsonNode numerator = formula == null ? null : formula.get("numerator");
        JsonNode denominator = formula == null ? null : formula.get("denominator");
        String n = aggregateSql(numerator);
        String d = aggregateSql(denominator);
        double multiply = formula != null && formula.has("multiply") ? formula.get("multiply").asDouble(1D) : 1D;
        String base = "case when " + d + " = 0 then null else " + n + " / " + d + " end";
        if (multiply == 1D) return base;
        return "(" + base + ") * " + stripTrailingZero(multiply);
    }

    private String aggregateSql(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        String type = defaultValue(readText(node, "type"), "sum").toLowerCase(Locale.ROOT);
        String field = safeField(required(readText(node, "field"), "metric field"));
        return switch (type) {
            case "count" -> "count(" + field + ")";
            case "count_distinct" -> "count(distinct " + field + ")";
            case "avg" -> "avg(" + field + ")";
            case "max" -> "max(" + field + ")";
            case "min" -> "min(" + field + ")";
            default -> "sum(" + field + ")";
        };
    }

    private String conditionSql(JsonNode formula) {
        String direct = readText(formula, "condition");
        if (StringUtils.hasText(direct)) return direct;
        String field = safeField(required(readText(formula, "field"), "condition field"));
        String operator = defaultValue(readText(formula, "operator"), "=");
        String value = readText(formula, "value");
        if (!StringUtils.hasText(value) && formula != null && formula.has("value")) {
            JsonNode node = formula.get("value");
            if (node.isBoolean() || node.isNumber()) return field + " " + operator + " " + node.asText();
        }
        return field + " " + operator + " '" + escapeSql(defaultValue(value, "")) + "'";
    }

    private JsonNode parseFormula(String formulaJson) {
        if (!StringUtils.hasText(formulaJson)) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(formulaJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("指标公式 JSON 不合法: " + ex.getMessage());
        }
    }

    private String readText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private boolean hasAggregateMetric(List<MetricDto> metrics) {
        return !metrics.isEmpty();
    }

    private void validateModelForReview(ModelDto model) {
        if (model.objectId() == null) {
            throw new IllegalArgumentException("模型未绑定业务对象，不能提交审核");
        }
        BusinessObjectDto object = getBusinessObject(model.objectId());
        if (!StringUtils.hasText(object.mainTable())) {
            throw new IllegalArgumentException("业务对象未配置主表，不能提交审核");
        }
        ModelBindingDto bindings = getModelBindings(model.id());
        if (bindings.metricIds() == null || bindings.metricIds().isEmpty()) {
            throw new IllegalArgumentException("模型未绑定输出指标，不能提交审核");
        }
        buildExecutableModelSql(
            model,
            object,
            selectBoundDimensions(model.objectId(), bindings.dimensionIds()),
            selectBoundMetrics(model.objectId(), bindings.metricIds())
        );
    }

    private void updateModelReview(UUID modelId, String status, String reviewStatus, String submittedBy, String reviewedBy, String comment) {
        jdbc.update(
            """
            update semantic_model
            set status = :status,
                review_status = :reviewStatus,
                submitted_by = coalesce(:submittedBy, submitted_by),
                submitted_at = case when :submittedBy is null then submitted_at else :now end,
                reviewed_by = :reviewedBy,
                reviewed_at = case when :reviewedBy is null then null else :now end,
                review_comment = :comment,
                last_modified_date = :now
            where id = :id
            """,
            params()
                .addValue("id", modelId)
                .addValue("status", status)
                .addValue("reviewStatus", reviewStatus)
                .addValue("submittedBy", trimToNull(submittedBy))
                .addValue("reviewedBy", trimToNull(reviewedBy))
                .addValue("comment", comment)
        );
    }

    private void appendReviewLog(UUID modelId, String action, String actor, String comment) {
        jdbc.update(
            """
            insert into semantic_model_review_log
                (id, model_id, action, actor, comment, created_date)
            values
                (:id, :modelId, :action, :actor, :comment, :now)
            """,
            params()
                .addValue("id", UUID.randomUUID())
                .addValue("modelId", modelId)
                .addValue("action", action)
                .addValue("actor", trimToNull(actor))
                .addValue("comment", trimToNull(comment))
        );
    }

    private List<String> extractPreviewHeaders(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("headers");
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> headers = new ArrayList<>();
        for (Object item : list) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                headers.add(String.valueOf(item));
            }
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPreviewRows(Object raw, int limit) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (rows.size() >= limit) break;
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((key, value) -> {
                    if (key != null) row.put(String.valueOf(key), value);
                });
                rows.add(row);
            }
        }
        return rows;
    }

    private String appendLimit(String sql, int limit) {
        String normalized = sql == null ? "" : sql.trim();
        normalized = normalized.replaceAll(";+$", "");
        if (normalized.toLowerCase(Locale.ROOT).matches("(?s).*\\blimit\\s+\\d+\\s*$")) {
            return normalized;
        }
        return normalized + "\nlimit " + limit;
    }

    private static long numberOrDefault(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void upsertDbtFile(String path, String content) {
        try {
            dbtFileService.readFile(path);
            dbtFileService.saveFile(path, content);
        } catch (IllegalArgumentException ex) {
            dbtFileService.createFile(path, "file", content);
        }
    }

    private <T> T listOne(String sql, MapSqlParameterSource params, org.springframework.jdbc.core.RowMapper<T> mapper) {
        List<T> rows = jdbc.query(sql, params, mapper);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("记录不存在");
        }
        return rows.get(0);
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource().addValue("now", Instant.now());
    }

    private MapSqlParameterSource metricParams(UUID id, MetricRequest request) {
        return params()
            .addValue("id", id)
            .addValue("objectId", request.objectId())
            .addValue("code", required(request.code(), "code"))
            .addValue("name", required(request.name(), "name"))
            .addValue("formulaType", trimToNull(request.formulaType()))
            .addValue("formulaJson", trimToNull(request.formulaJson()))
            .addValue("format", trimToNull(request.format()))
            .addValue("unit", trimToNull(request.unit()))
            .addValue("status", defaultValue(request.status(), "DRAFT"));
    }

    private MapSqlParameterSource modelParams(UUID id, ModelRequest request) {
        return params()
            .addValue("id", id)
            .addValue("objectId", request.objectId())
            .addValue("type", trimToNull(request.type()))
            .addValue("name", required(request.name(), "name"))
            .addValue("tableName", trimToNull(request.tableName()))
            .addValue("description", trimToNull(request.description()))
            .addValue("grain", trimToNull(request.grain()))
            .addValue("materialization", trimToNull(request.materialization()))
            .addValue("refreshCycle", trimToNull(request.refreshCycle()))
            .addValue("status", defaultValue(request.status(), "DRAFT"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String text && StringUtils.hasText(text)) return UUID.fromString(text);
        return null;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long longValue(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String required(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return normalized;
    }

    private static String safeIdentifier(String value) {
        String normalized = defaultValue(value, "semantic_model").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) normalized = "semantic_model";
        if (!Character.isLetter(normalized.charAt(0))) normalized = "m_" + normalized;
        return normalized;
    }

    private static String safeField(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        if ("*".equals(normalized)) return "*";
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            throw new IllegalArgumentException("字段名不合法: " + value);
        }
        return normalized;
    }

    private static String safeTableRef(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            throw new IllegalArgumentException("表名不合法: " + value);
        }
        return normalized;
    }

    private static String safeJoinExpression(String value) {
        String normalized = required(value, "joinExpression");
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (
            normalized.contains(";") ||
            normalized.contains("--") ||
            normalized.contains("/*") ||
            lower.matches(".*\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke)\\b.*")
        ) {
            throw new IllegalArgumentException("Join 条件不合法");
        }
        if (!normalized.matches("[A-Za-z0-9_\\.\\s=<>!()'\"-]+")) {
            throw new IllegalArgumentException("Join 条件包含不支持的字符");
        }
        return normalized;
    }

    private static String joinKeyword(String tableRole) {
        String role = defaultValue(tableRole, "LEFT").toUpperCase(Locale.ROOT);
        if (role.contains("INNER")) return "inner join";
        if (role.contains("FULL")) return "full join";
        if (role.contains("RIGHT")) return "right join";
        return "left join";
    }

    private static String defaultValue(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private static String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String escapeYaml(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stripTrailingZero(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    public record SubjectDomainDto(
        UUID id,
        String code,
        String name,
        String description,
        String status,
        String ownerDept,
        UUID governanceDomainId,
        String governanceDomainCode,
        String governanceDomainName
    ) {}
    public record SubjectDomainRequest(
        String code,
        String name,
        String description,
        String status,
        String ownerDept,
        UUID governanceDomainId,
        String governanceDomainCode,
        String governanceDomainName
    ) {}

    public record BusinessObjectDto(UUID id, UUID domainId, String code, String name, String description, String primaryKey, String mainTable, String status, String ownerDept) {}
    public record BusinessObjectRequest(UUID domainId, String code, String name, String description, String primaryKey, String mainTable, String status, String ownerDept) {}
    public record ObjectTableMappingDto(UUID id, UUID objectId, String tableName, String tableRole, String joinExpression, Integer sortOrder) {}
    public record ObjectTableMappingRequest(String tableName, String tableRole, String joinExpression, Integer sortOrder) {}
    public record ObjectTableMappingSaveRequest(List<ObjectTableMappingRequest> mappings) {}

    public record DimensionDto(UUID id, UUID objectId, String code, String name, String fieldName, String dataType, String semanticType, String status) {}
    public record DimensionRequest(UUID objectId, String code, String name, String fieldName, String dataType, String semanticType, String status) {}

    public record MetricDto(UUID id, UUID objectId, String code, String name, String formulaType, String formulaJson, String format, String unit, String status) {}
    public record MetricRequest(UUID objectId, String code, String name, String formulaType, String formulaJson, String format, String unit, String status) {}

    public record ModelDto(
        UUID id,
        UUID objectId,
        String type,
        String name,
        String tableName,
        String description,
        String grain,
        String materialization,
        String refreshCycle,
        String status,
        String reviewStatus,
        String submittedBy,
        Instant submittedAt,
        String reviewedBy,
        Instant reviewedAt,
        String reviewComment
    ) {}
    public record ModelRequest(UUID objectId, String type, String name, String tableName, String description, String grain, String materialization, String refreshCycle, String status) {}
    public record ModelBindingDto(UUID modelId, List<UUID> dimensionIds, List<UUID> metricIds) {}
    public record ModelBindingRequest(List<UUID> dimensionIds, List<UUID> metricIds) {}
    public record ReviewActionRequest(String comment) {}
    public record ModelReviewLogDto(UUID id, UUID modelId, String action, String actor, String comment, Instant createdDate) {}
    public record ModelRunRequest(String runType, String dagSelector, String target, Map<String, Object> vars) {}
    public record ModelRunStatusRequest(String status, String externalRunId, Long durationMs, String message, Map<String, Object> payload) {}
    public record ModelRunDto(
        UUID id,
        UUID modelId,
        String runType,
        String selector,
        String dagId,
        String externalRunId,
        String status,
        String triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String message,
        String payloadJson,
        Instant createdDate
    ) {}

    public record GeneratedArtifactDto(UUID id, UUID modelId, String artifactType, String path, String content, String status) {}
    public record GenerateArtifactsResult(ModelDto model, List<GeneratedArtifactDto> artifacts) {}
    public record ModelPreviewResult(
        boolean success,
        String sql,
        List<String> headers,
        List<Map<String, Object>> rows,
        long rowCount,
        long durationMs,
        String errorMessage
    ) {}
    public record PublishArtifactsResult(UUID modelId, List<String> publishedPaths) {}
    public record RegisterBiDatasetResult(UUID modelId, UUID datasetId, String datasetName, Integer versionNo) {}
    public record RegisterLineageResult(UUID modelId, UUID lineageId, UUID upstreamDatasetId, UUID downstreamDatasetId) {}
}
