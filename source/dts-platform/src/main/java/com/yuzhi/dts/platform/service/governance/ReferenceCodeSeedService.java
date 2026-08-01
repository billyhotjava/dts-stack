package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.modeling.LegacyCodeSetMigrationPort;
import com.yuzhi.dts.platform.service.modeling.LegacyCodeSetMigrationPort.LegacyCodeSetCandidate;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ReferenceCodeSeedService {

    private final StdCodeDirectoryRepository directoryRepository;
    private final StdCodeValueRepository valueRepository;
    private final LegacyCodeSetMigrationPort legacyCodeSets;
    private final DbtConfigService dbtConfigService;
    private final DbtProperties dbtProperties;
    private final ReferenceCodeSecurity security;

    public ReferenceCodeSeedService(
        StdCodeDirectoryRepository directoryRepository,
        StdCodeValueRepository valueRepository,
        LegacyCodeSetMigrationPort legacyCodeSets,
        DbtConfigService dbtConfigService,
        DbtProperties dbtProperties,
        ReferenceCodeSecurity security
    ) {
        this.directoryRepository = directoryRepository;
        this.valueRepository = valueRepository;
        this.legacyCodeSets = legacyCodeSets;
        this.dbtConfigService = dbtConfigService;
        this.dbtProperties = dbtProperties;
        this.security = security;
    }

    public Map<String, Object> syncSeeds(String activeDept) {
        LegacyMigrationStats legacyStats = migrateLegacyCodeSets();
        List<StdCodeDirectory> directories = loadDirectories(activeDept);
        Path output = resolveSeedPath();
        SeedStats stats = writeSeedFile(directories, output);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seedPath", output.toString());
        payload.put("directories", directories.size());
        payload.put("rows", stats.rows);
        payload.put("legacyDirectories", legacyStats.directoriesCreated);
        payload.put("legacyItems", legacyStats.itemsCreated);
        payload.put("legacyStandardsUpdated", legacyStats.standardsUpdated);
        return payload;
    }

    private List<StdCodeDirectory> loadDirectories(String activeDept) {
        List<StdCodeDirectory> all = directoryRepository.findAll();
        if (security.hasInstituteScope()) {
            return all;
        }
        String dept = security.resolveActiveDept(activeDept);
        if (!StringUtils.hasText(dept)) {
            return all;
        }
        List<StdCodeDirectory> filtered = new ArrayList<>();
        for (StdCodeDirectory dir : all) {
            if (dept.equalsIgnoreCase(trimToEmpty(dir.getOwnerDept()))) {
                filtered.add(dir);
            }
        }
        return filtered;
    }

    private Path resolveSeedPath() {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (!view.enabled() || view.config() == null || !StringUtils.hasText(view.config().projectDir())) {
            String fallback = dbtProperties.getProjectDir();
            if (!StringUtils.hasText(fallback)) {
                throw new IllegalStateException("dbt 项目目录未配置");
            }
            return ensureSeedDir(Path.of(fallback)).resolve("reference_codes.csv");
        }
        return ensureSeedDir(Path.of(view.config().projectDir())).resolve("reference_codes.csv");
    }

    private Path ensureSeedDir(Path projectDir) {
        Path seedsDir = projectDir.resolve("seeds");
        try {
            Files.createDirectories(seedsDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 seeds 目录: " + seedsDir, e);
        }
        return seedsDir;
    }

    private SeedStats writeSeedFile(List<StdCodeDirectory> directories, Path file) {
        int rows = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("code_type_code,code_value,code_name,description,sort_num,parent_code,is_default,owner_dept,version");
            writer.newLine();
            for (StdCodeDirectory directory : directories) {
                List<StdCodeValue> values = valueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(directory.getCodeTypeId());
                for (StdCodeValue value : values) {
                    writer.write(csv(directory.getCodeTypeCode()));
                    writer.write(",");
                    writer.write(csv(value.getCodeValue()));
                    writer.write(",");
                    writer.write(csv(value.getCodeName()));
                    writer.write(",");
                    writer.write(csv(value.getDescription()));
                    writer.write(",");
                    writer.write(value.getSortNum() == null ? "" : String.valueOf(value.getSortNum()));
                    writer.write(",");
                    writer.write(csv(value.getParentCode()));
                    writer.write(",");
                    writer.write(value.getIsDefault() == null ? "" : String.valueOf(value.getIsDefault()));
                    writer.write(",");
                    writer.write(csv(directory.getOwnerDept()));
                    writer.write(",");
                    writer.write(csv(directory.getVersion()));
                    writer.newLine();
                    rows++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入 dbt seeds 失败: " + file, e);
        }
        return new SeedStats(rows);
    }

    private LegacyMigrationStats migrateLegacyCodeSets() {
        List<LegacyCodeSetCandidate> standards = legacyCodeSets.findCandidates();
        int directoriesCreated = 0;
        int itemsCreated = 0;
        int standardsUpdated = 0;
        for (LegacyCodeSetCandidate standard : standards) {
            String raw = trimToNull(standard.inlineCodeSet());
            String code = trimToNull(standard.code());
            String codeTypeId = code;
            String codeTypeCode = code;
            List<CodePair> pairs = parseCodeSet(raw);
            if (!legacyCodeSets.replaceInlineCodeSet(standard.id(), standard.inlineCodeSet(), codeTypeCode)) {
                continue;
            }
            standardsUpdated++;
            StdCodeDirectory directory = directoryRepository.findByCodeTypeCodeIgnoreCase(codeTypeCode).orElse(null);
            if (directory == null) {
                directory = new StdCodeDirectory();
                directory.setCodeTypeId(codeTypeId);
                directory.setCodeTypeCode(codeTypeCode);
                directory.setCodeTypeName(trimToNull(standard.name()) != null ? standard.name() : codeTypeCode);
                directory.setBizCatalog(trimToNull(standard.domain()));
                directory.setDataType(trimToNull(standard.dataType()));
                directory.setStatus(1);
                directory.setVersion(trimToNull(standard.currentVersion()));
                directoryRepository.save(directory);
                directoriesCreated++;
            }
            Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (CodePair pair : pairs) {
                if (!StringUtils.hasText(pair.value) || !StringUtils.hasText(pair.label)) {
                    continue;
                }
                if (seen.contains(pair.value)) {
                    continue;
                }
                if (valueRepository.existsByCodeTypeIdAndCodeValue(directory.getCodeTypeId(), pair.value)) {
                    continue;
                }
                StdCodeValue value = new StdCodeValue();
                value.setCodeTypeId(directory.getCodeTypeId());
                value.setCodeValue(pair.value);
                value.setCodeName(pair.label);
                value.setSortNum(pair.order);
                valueRepository.save(value);
                itemsCreated++;
                seen.add(pair.value);
            }
        }
        return new LegacyMigrationStats(directoriesCreated, itemsCreated, standardsUpdated);
    }

    private List<CodePair> parseCodeSet(String raw) {
        String normalized = raw.replace("\n", ",").replace(";", ",");
        String[] parts = normalized.split(",");
        List<CodePair> pairs = new ArrayList<>();
        int order = 1;
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String trimmed = part.trim();
            int idx = trimmed.indexOf(':');
            if (idx < 0) {
                continue;
            }
            String value = trimToNull(trimmed.substring(0, idx));
            String label = trimToNull(trimmed.substring(idx + 1));
            if (!StringUtils.hasText(value) || !StringUtils.hasText(label)) {
                continue;
            }
            pairs.add(new CodePair(value, label, order++));
        }
        return pairs;
    }

    private String csv(String value) {
        if (value == null) return "";
        String v = value;
        boolean needQuotes = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (v.contains("\"")) {
            v = v.replace("\"", "\"\"");
        }
        if (needQuotes) {
            v = "\"" + v + "\"";
        }
        return v;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record SeedStats(int rows) {}

    private record LegacyMigrationStats(int directoriesCreated, int itemsCreated, int standardsUpdated) {}

    private record CodePair(String value, String label, int order) {}
}
