package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.config.PersonnelSyncProperties;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersonnelExcelParserTest {

    private PersonnelExcelParser parser;

    @BeforeEach
    void setUp() {
        PersonnelSyncProperties properties = new PersonnelSyncProperties();
        properties.getExcel().setEnabled(true);
        properties.getExcel().setMaxRows(10);
        parser = new PersonnelExcelParser(properties);
    }

    @Test
    void shouldParseSimpleSheet() throws Exception {
        byte[] content = buildWorkbook();
        List<PersonnelPayload> payloads = parser.parse(new ByteArrayInputStream(content), "人员.xlsx");
        assertThat(payloads).hasSize(1);
        PersonnelPayload payload = payloads.get(0);
        assertThat(payload.personCode()).isEqualTo("P-001");
        assertThat(payload.fullName()).isEqualTo("张三");
        assertThat(payload.deptCode()).isEqualTo("D-01");
    }

    private byte[] buildWorkbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var baos = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("人员");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("person_code");
            header.createCell(1).setCellValue("full_name");
            header.createCell(2).setCellValue("dept_code");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("P-001");
            row.createCell(1).setCellValue("张三");
            row.createCell(2).setCellValue("D-01");
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}
