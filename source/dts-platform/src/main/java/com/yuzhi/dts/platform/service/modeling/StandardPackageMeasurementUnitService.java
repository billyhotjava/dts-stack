package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.governance.MeasurementUnitApplicationService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Sixth-file adapter that keeps measurement units on their versioned application-service write path. */
@Service
public class StandardPackageMeasurementUnitService {

    public static final String FILE_NAME = "06-measurement-units.csv";
    public static final String ENTITY_TYPE = "MEASUREMENT_UNIT";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";

    private static final List<String> REQUIRED_HEADERS = List.of(
        "code",
        "name",
        "symbol",
        "quantity_kind",
        "conversion_factor",
        "precision",
        "status"
    );
    private static final String APPLIED_VERSION = "_appliedVersion";
    private static final String APPLIED_CHECKSUM = "_appliedChecksum";

    private final MeasurementUnitApplicationService units;

    public StandardPackageMeasurementUnitService(MeasurementUnitApplicationService units) {
        this.units = units;
    }

    public Preview preview(byte[] content) {
        if (content == null) {
            return new Preview(report(false, 0, 0, 0, List.of()), List.of());
        }
        ParsedCsv csv = parse(content);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<String> missingHeaders = REQUIRED_HEADERS.stream().filter(header -> !csv.headerIndex().containsKey(header)).toList();
        if (!missingHeaders.isEmpty()) {
            errors.add(error(1, "缺少必需表头：" + String.join(",", missingHeaders)));
            return new Preview(report(true, 0, 0, 0, errors), List.of());
        }

        Map<String, MeasurementUnitView> existingByCode = existingByCode();
        Map<String, MeasurementUnitView> existingBySymbol = new HashMap<>();
        for (MeasurementUnitView existing : existingByCode.values()) {
            existingBySymbol.putIfAbsent(existing.symbol(), existing);
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Set<String> symbols = new HashSet<>();
        int total = 0;
        for (RawRow row : csv.rows()) {
            total++;
            Candidate candidate = candidate(csv, row, errors);
            if (candidate == null) continue;
            String codeKey = key(candidate.command().code());
            if (candidates.containsKey(codeKey)) {
                errors.add(error(row.lineNumber(), "code 在文件内重复：" + candidate.command().code()));
                continue;
            }
            if (!symbols.add(candidate.command().symbol())) {
                errors.add(error(row.lineNumber(), "symbol 在文件内重复：" + candidate.command().symbol()));
                continue;
            }
            MeasurementUnitView symbolOwner = existingBySymbol.get(candidate.command().symbol());
            if (symbolOwner != null && !key(symbolOwner.code()).equals(codeKey)) {
                errors.add(
                    error(
                        row.lineNumber(),
                        "symbol 已被其他计量单位使用：" + candidate.command().symbol() + " (" + symbolOwner.code() + ")"
                    )
                );
                continue;
            }
            candidates.put(codeKey, candidate);
        }

        validateBases(candidates, existingByCode, errors);
        validateCycles(candidates, errors);

        int toCreate = 0;
        int toUpdate = 0;
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
            if (existingByCode.containsKey(entry.getKey())) toUpdate++;
            else toCreate++;
            payload.add(toPayload(entry.getValue()));
        }
        return new Preview(report(true, total, toCreate, toUpdate, errors), List.copyOf(payload));
    }

    public List<Change> apply(List<Map<String, Object>> payload, String actor) {
        Map<String, MeasurementUnitView> currentByCode = existingByCode();
        List<Map<String, Object>> pending = new ArrayList<>(payload);
        List<Change> changes = new ArrayList<>();
        while (!pending.isEmpty()) {
            int appliedThisPass = 0;
            for (int index = 0; index < pending.size();) {
                Map<String, Object> row = pending.get(index);
                String baseCode = asString(row.get("baseUnitCode"));
                MeasurementUnitView base = StringUtils.hasText(baseCode) ? currentByCode.get(key(baseCode)) : null;
                if (StringUtils.hasText(baseCode) && base == null) {
                    index++;
                    continue;
                }
                String code = asString(row.get("code"));
                MeasurementUnitView before = currentByCode.get(key(code));
                MeasurementUnitCommand command = new MeasurementUnitCommand(
                    code,
                    asString(row.get("name")),
                    asString(row.get("symbol")),
                    asString(row.get("quantityKind")),
                    asDecimal(row.get("conversionFactor")),
                    base == null ? null : base.id(),
                    asInteger(row.get("precision"))
                );
                MeasurementUnitView applied;
                String action;
                if (before == null) {
                    applied = units.create(actor, command);
                    action = ACTION_CREATE;
                } else {
                    applied = units.replaceForImport(
                        actor,
                        before.id(),
                        expected(before),
                        command,
                        MeasurementUnitStatus.ACTIVE
                    );
                    action = ACTION_UPDATE;
                }
                currentByCode.put(key(applied.code()), applied);
                Map<String, Object> rollbackImage = before == null ? new LinkedHashMap<>() : image(before);
                rollbackImage.put(APPLIED_VERSION, applied.version());
                rollbackImage.put(APPLIED_CHECKSUM, applied.checksum());
                changes.add(new Change(applied.id().toString(), action, rollbackImage));
                pending.remove(index);
                appliedThisPass++;
            }
            if (appliedThisPass == 0) {
                throw new IllegalStateException("计量单位包应用失败：base_unit_code 依赖无法收敛");
            }
        }
        return List.copyOf(changes);
    }

    public boolean rollbackCreate(UUID unitId, Map<String, Object> rollbackImage, String actor) {
        try {
            units.rollbackCreatedImport(actor, unitId, appliedExpected(unitId, rollbackImage));
            return true;
        } catch (MeasurementUnitException exception) {
            if ("MEASUREMENT_UNIT_NOT_FOUND".equals(exception.code())) return false;
            throw exception;
        }
    }

    public boolean rollbackUpdate(UUID unitId, Map<String, Object> rollbackImage, String actor) {
        try {
            MeasurementUnitCommand command = new MeasurementUnitCommand(
                asString(rollbackImage.get("code")),
                asString(rollbackImage.get("name")),
                asString(rollbackImage.get("symbol")),
                asString(rollbackImage.get("quantityKind")),
                asDecimal(rollbackImage.get("conversionFactor")),
                asUuid(rollbackImage.get("baseUnitRef")),
                asInteger(rollbackImage.get("precision"))
            );
            MeasurementUnitStatus status = MeasurementUnitStatus.valueOf(asString(rollbackImage.get("status")));
            units.replaceForImport(actor, unitId, appliedExpected(unitId, rollbackImage), command, status);
            return true;
        } catch (MeasurementUnitException exception) {
            if ("MEASUREMENT_UNIT_NOT_FOUND".equals(exception.code())) return false;
            throw exception;
        }
    }

    private Candidate candidate(ParsedCsv csv, RawRow row, List<Map<String, Object>> errors) {
        String status = col(csv, row, "status");
        if (!MeasurementUnitStatus.ACTIVE.name().equalsIgnoreCase(status)) {
            errors.add(error(row.lineNumber(), "status 仅允许 ACTIVE"));
            return null;
        }
        BigDecimal factor;
        Integer precision;
        try {
            factor = new BigDecimal(String.valueOf(col(csv, row, "conversion_factor")));
        } catch (Exception exception) {
            errors.add(error(row.lineNumber(), "conversion_factor 需为有效数字"));
            return null;
        }
        try {
            precision = Integer.valueOf(String.valueOf(col(csv, row, "precision")));
        } catch (Exception exception) {
            errors.add(error(row.lineNumber(), "precision 需为整数"));
            return null;
        }
        MeasurementUnitCommand command = MeasurementUnitContract.normalize(
            new MeasurementUnitCommand(
                col(csv, row, "code"),
                col(csv, row, "name"),
                col(csv, row, "symbol"),
                col(csv, row, "quantity_kind"),
                factor,
                null,
                precision
            )
        );
        List<MeasurementUnitContract.FieldIssue> issues = MeasurementUnitContract.validate(command);
        if (!issues.isEmpty()) {
            errors.add(error(row.lineNumber(), issues.get(0).message()));
            return null;
        }
        String baseCode = col(csv, row, "base_unit_code");
        return new Candidate(row.lineNumber(), command, StringUtils.hasText(baseCode) ? baseCode.toUpperCase(Locale.ROOT) : null);
    }

    private void validateBases(
        Map<String, Candidate> candidates,
        Map<String, MeasurementUnitView> existingByCode,
        List<Map<String, Object>> errors
    ) {
        for (Candidate candidate : candidates.values()) {
            if (!StringUtils.hasText(candidate.baseUnitCode())) continue;
            if (key(candidate.command().code()).equals(key(candidate.baseUnitCode()))) {
                errors.add(error(candidate.lineNumber(), candidate.command().code() + " 的 base_unit_code 不得引用自身"));
                continue;
            }
            Candidate packageBase = candidates.get(key(candidate.baseUnitCode()));
            String baseQuantity = packageBase == null
                ? quantityOf(existingByCode.get(key(candidate.baseUnitCode())))
                : packageBase.command().quantityKind();
            if (!StringUtils.hasText(baseQuantity)) {
                errors.add(error(candidate.lineNumber(), "base_unit_code 无法解析：" + candidate.baseUnitCode()));
            } else if (!baseQuantity.equals(candidate.command().quantityKind())) {
                errors.add(error(candidate.lineNumber(), "base_unit_code 与当前单位量纲不一致：" + candidate.baseUnitCode()));
            }
            MeasurementUnitView existingBase = existingByCode.get(key(candidate.baseUnitCode()));
            if (packageBase == null && existingBase != null && existingBase.status() != MeasurementUnitStatus.ACTIVE) {
                errors.add(error(candidate.lineNumber(), "base_unit_code 指向已停用单位：" + candidate.baseUnitCode()));
            }
        }
    }

    private void validateCycles(Map<String, Candidate> candidates, List<Map<String, Object>> errors) {
        Set<String> resolved = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String code : candidates.keySet()) {
            detectCycle(code, candidates, resolved, visiting, errors);
        }
    }

    private void detectCycle(
        String code,
        Map<String, Candidate> candidates,
        Set<String> resolved,
        Set<String> visiting,
        List<Map<String, Object>> errors
    ) {
        if (resolved.contains(code)) return;
        Candidate candidate = candidates.get(code);
        if (candidate == null) return;
        if (!visiting.add(code)) {
            errors.add(error(candidate.lineNumber(), "base_unit_code 存在循环引用：" + candidate.command().code()));
            return;
        }
        String baseCode = key(candidate.baseUnitCode());
        if (candidates.containsKey(baseCode)) detectCycle(baseCode, candidates, resolved, visiting, errors);
        visiting.remove(code);
        resolved.add(code);
    }

    private ParsedCsv parse(byte[] content) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        List<RawRow> rows = new ArrayList<>();
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8)
            )
        ) {
            String headerLine = reader.readLine();
            if (headerLine != null) {
                List<String> headers = CsvUtils.parseCsvLine(CsvUtils.stripBom(headerLine));
                for (int index = 0; index < headers.size(); index++) {
                    String header = headers.get(index);
                    if (StringUtils.hasText(header)) headerIndex.put(header.trim().toLowerCase(Locale.ROOT), index);
                }
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (StringUtils.hasText(line)) rows.add(new RawRow(lineNumber, CsvUtils.parseCsvLine(line)));
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException(FILE_NAME + " 解析失败：" + exception.getMessage());
        }
        return new ParsedCsv(headerIndex, rows);
    }

    private String col(ParsedCsv csv, RawRow row, String key) {
        Integer index = csv.headerIndex().get(key);
        if (index == null || index >= row.values().size()) return null;
        String value = row.values().get(index);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, MeasurementUnitView> existingByCode() {
        Map<String, MeasurementUnitView> result = new LinkedHashMap<>();
        for (MeasurementUnitView unit : units.list()) result.put(key(unit.code()), unit);
        return result;
    }

    private Map<String, Object> toPayload(Candidate candidate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", candidate.command().code());
        row.put("name", candidate.command().name());
        row.put("symbol", candidate.command().symbol());
        row.put("quantityKind", candidate.command().quantityKind());
        row.put("conversionFactor", candidate.command().conversionFactor());
        row.put("baseUnitCode", candidate.baseUnitCode());
        row.put("precision", candidate.command().precision());
        row.put("status", MeasurementUnitStatus.ACTIVE.name());
        return row;
    }

    private Map<String, Object> image(MeasurementUnitView unit) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("code", unit.code());
        image.put("name", unit.name());
        image.put("symbol", unit.symbol());
        image.put("quantityKind", unit.quantityKind());
        image.put("conversionFactor", unit.conversionFactor());
        image.put("baseUnitRef", unit.baseUnitRef() == null ? null : unit.baseUnitRef().toString());
        image.put("precision", unit.precision());
        image.put("status", unit.status().name());
        image.put("version", unit.version());
        image.put("checksum", unit.checksum());
        return image;
    }

    private Map<String, Object> report(
        boolean present,
        int total,
        int toCreate,
        int toUpdate,
        List<Map<String, Object>> errors
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("file", FILE_NAME);
        report.put("present", present);
        report.put("total", total);
        report.put("toCreate", toCreate);
        report.put("toUpdate", toUpdate);
        report.put("errorCount", errors.size());
        report.put("errors", errors);
        return report;
    }

    private Map<String, Object> error(int row, String message) {
        return Map.of("row", row, "message", message);
    }

    private ExpectedVersion appliedExpected(UUID unitId, Map<String, Object> image) {
        Integer version = asInteger(image.get(APPLIED_VERSION));
        String checksum = asString(image.get(APPLIED_CHECKSUM));
        if (version == null || !StringUtils.hasText(checksum)) {
            throw new IllegalStateException("计量单位回滚证据缺失");
        }
        return new ExpectedVersion(unitId, version, checksum);
    }

    private ExpectedVersion expected(MeasurementUnitView view) {
        return new ExpectedVersion(view.id(), view.version(), view.checksum());
    }

    private String quantityOf(MeasurementUnitView unit) {
        return unit == null ? null : unit.quantityKind();
    }

    private String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        return StringUtils.hasText(asString(value)) ? Integer.valueOf(asString(value)) : null;
    }

    private BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(asString(value));
    }

    private UUID asUuid(Object value) {
        return StringUtils.hasText(asString(value)) ? UUID.fromString(asString(value)) : null;
    }

    public record Preview(Map<String, Object> report, List<Map<String, Object>> payload) {}

    public record Change(String entityId, String action, Map<String, Object> rollbackImage) {}

    private record ParsedCsv(Map<String, Integer> headerIndex, List<RawRow> rows) {}

    private record RawRow(int lineNumber, List<String> values) {}

    private record Candidate(int lineNumber, MeasurementUnitCommand command, String baseUnitCode) {}
}
