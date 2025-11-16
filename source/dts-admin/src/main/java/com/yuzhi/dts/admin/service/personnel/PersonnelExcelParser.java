package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.config.PersonnelSyncProperties;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PersonnelExcelParser {

    private static final Logger LOG = LoggerFactory.getLogger(PersonnelExcelParser.class);

    private final PersonnelSyncProperties properties;

    public PersonnelExcelParser(PersonnelSyncProperties properties) {
        this.properties = properties;
    }

    public List<PersonnelPayload> parse(InputStream inputStream, String filename) {
        if (!properties.getExcel().isEnabled()) {
            throw new PersonnelImportException("Excel 导入已被禁用");
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new PersonnelImportException("Excel 文件为空");
            }
            Iterator<Row> iterator = sheet.rowIterator();
            if (!iterator.hasNext()) {
                throw new PersonnelImportException("Excel 文件缺少表头");
            }
            Row header = iterator.next();
            Map<Integer, String> headerMapping = resolveHeaders(header);
            List<PersonnelPayload> payloads = new ArrayList<>();
            while (iterator.hasNext()) {
                Row row = iterator.next();
                if (isEmptyRow(row)) {
                    continue;
                }
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headerMapping.entrySet()) {
                    Cell cell = row.getCell(entry.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    Object value = readCell(cell);
                    if (value != null) {
                        rowMap.put(entry.getValue(), value);
                    }
                }
                if (rowMap.isEmpty()) {
                    continue;
                }
                payloads.add(PersonnelPayloadMapper.fromMap(rowMap));
                if (payloads.size() > properties.getExcel().getMaxRows()) {
                    throw new PersonnelImportException("Excel 记录数超过限制：" + properties.getExcel().getMaxRows());
                }
            }
            return payloads;
        } catch (PersonnelImportException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new PersonnelImportException("解析 Excel 失败: " + ex.getMessage(), ex);
        }
    }

    private Map<Integer, String> resolveHeaders(Row header) {
        Map<Integer, String> mapping = new LinkedHashMap<>();
        for (Cell cell : header) {
            String name = readString(cell);
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String normalized = name.trim();
            mapping.put(cell.getColumnIndex(), normalized);
        }
        if (mapping.isEmpty()) {
            throw new PersonnelImportException("Excel 表头为空");
        }
        return mapping;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK && StringUtils.hasText(readString(cell))) {
                return false;
            }
        }
        return true;
    }

    private Object readCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> readString(cell);
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue() : cell.getNumericCellValue();
            case FORMULA -> evaluateFormula(cell);
            default -> null;
        };
    }

    private Object evaluateFormula(Cell cell) {
        try {
            FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
            CellValue value = evaluator.evaluate(cell);
            if (value == null) {
                return null;
            }
            return switch (value.getCellType()) {
                case STRING -> value.getStringValue();
                case BOOLEAN -> value.getBooleanValue();
                case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue() : value.getNumberValue();
                default -> null;
            };
        } catch (Exception ex) {
            LOG.debug("Failed to evaluate formula at row {}, column {}: {}", cell.getRowIndex(), cell.getColumnIndex(), ex.getMessage());
            return null;
        }
    }

    private String readString(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            return cell.getStringCellValue();
        } catch (IllegalStateException ex) {
            if (cell.getCellType() == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                if (Math.floor(value) == value) {
                    return Long.toString((long) value);
                }
                return Double.toString(value);
            }
            return null;
        }
    }
}
