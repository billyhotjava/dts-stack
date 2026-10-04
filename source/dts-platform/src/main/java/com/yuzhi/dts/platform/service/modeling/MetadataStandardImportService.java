package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunItemRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardImportResultDto;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class MetadataStandardImportService {

    /** 旧单文件直导路径的 run 来源标识，与标准包 UPLOAD/BUILTIN 区分。 */
    public static final String SOURCE_LEGACY_SINGLE = "LEGACY_SINGLE";

    private final MetadataStandardRepository repository;
    private final MetadataStandardService service;
    private final StandardPackageImportRunRepository runRepository;
    private final StandardPackageImportRunItemRepository runItemRepository;
    private final ObjectMapper objectMapper;

    public MetadataStandardImportService(
        MetadataStandardRepository repository,
        MetadataStandardService service,
        StandardPackageImportRunRepository runRepository,
        StandardPackageImportRunItemRepository runItemRepository,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.service = service;
        this.runRepository = runRepository;
        this.runItemRepository = runItemRepository;
        this.objectMapper = objectMapper;
    }

    public MetadataStandardImportResultDto importCsv(MultipartFile file) {
        MetadataStandardImportResultDto result = new MetadataStandardImportResultDto();
        if (file == null || file.isEmpty()) {
            result.addError("未上传文件或文件为空");
            return result;
        }
        List<StandardPackageImportRunItem> runItems = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                result.addError("CSV 表头为空");
                return result;
            }
            List<String> headers = CsvUtils.parseCsvLine(CsvUtils.stripBom(headerLine));
            Map<String, Integer> index = buildHeaderIndex(headers);
            if (!index.containsKey("field_name_cn") || !index.containsKey("field_name_en")) {
                result.addError("CSV 必须包含表头：field_name_cn,field_name_en");
                return result;
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> values = CsvUtils.parseCsvLine(line);
                MetadataStandardCsvSupport.ElementRow row = MetadataStandardCsvSupport.buildElementRow(key -> get(values, index, key));
                if (row.hasError()) {
                    result.addError("第 " + rowNumber + " 行：" + row.error());
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                MetadataStandardUpsertRequest req = row.request();
                try {
                    Optional<MetadataStandard> existing = repository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase(
                        req.getFieldNameEn(),
                        req.getDomain()
                    );
                    existing.ifPresentOrElse(
                        standard -> {
                            runItems.add(runItem(runItems.size() + 1, standard.getId().toString(), "UPDATE", MetadataStandardCsvSupport.elementImage(standard)));
                            service.update(standard.getId(), req);
                            result.setUpdated(result.getUpdated() + 1);
                        },
                        () -> {
                            var dto = service.create(req);
                            runItems.add(runItem(runItems.size() + 1, String.valueOf(dto.getId()), "CREATE", null));
                            result.setCreated(result.getCreated() + 1);
                        }
                    );
                } catch (RuntimeException ex) {
                    result.addError("第 " + rowNumber + " 行：" + req.getFieldNameEn() + " 导入失败：" + safeMessage(ex));
                    result.setSkipped(result.getSkipped() + 1);
                } finally {
                    result.setTotalRows(result.getTotalRows() + 1);
                }
            }
        } catch (Exception ex) {
            result.addError("读取 CSV 失败：" + safeMessage(ex));
        }
        if (!runItems.isEmpty()) {
            StandardPackageImportRun run = new StandardPackageImportRun();
            run.setPackageName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "metadata-standards.csv");
            run.setSource(SOURCE_LEGACY_SINGLE);
            run.setStatus(StandardPackageApplyService.STATUS_APPLIED);
            run.setSummary("数据元单文件导入：新增 " + result.getCreated() + "，更新 " + result.getUpdated());
            run.setCreatedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
            StandardPackageImportRun saved = runRepository.save(run);
            for (StandardPackageImportRunItem item : runItems) {
                item.setRunId(saved.getId());
            }
            runItemRepository.saveAll(runItems);
        }
        return result;
    }

    private StandardPackageImportRunItem runItem(int seq, String entityId, String action, Map<String, Object> beforeImage) {
        StandardPackageImportRunItem item = new StandardPackageImportRunItem();
        item.setSeq(seq);
        item.setEntityType("ELEMENT");
        item.setEntityId(entityId);
        item.setAction(action);
        if (beforeImage != null) {
            try {
                item.setBeforeJson(objectMapper.writeValueAsString(beforeImage));
            } catch (Exception ex) {
                throw new IllegalStateException("before-image 序列化失败");
            }
        }
        return item;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (!StringUtils.hasText(h)) continue;
            String key = h.trim().toLowerCase(Locale.ROOT);
            index.put(key, i);
        }
        return index;
    }

    private String get(List<String> values, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null) return null;
        if (i < 0 || i >= values.size()) return null;
        String v = values.get(i);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        return StringUtils.hasText(msg) ? msg : ex.getClass().getSimpleName();
    }
}
