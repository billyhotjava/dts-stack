package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvParseServiceTest {

    private final CsvParseService service = new CsvParseService();

    @Test
    void parseShouldSupportQuotedCommasQuotesAndMultilineFields() throws Exception {
        String csv = "\uFEFF项目名称,金额,备注\n"
            + "\"一期,二期\",123,\"他说\"\"通过\"\"\"\n"
            + "\"跨行项目\",45.67,\"第一行\n第二行\"\n";

        var result = service.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.columns()).extracting("name").containsExactly("name", "amount", "field_3");
        assertThat(result.rows().get(0)).containsExactly("一期,二期", "123", "他说\"通过\"");
        assertThat(result.rows().get(1)).containsExactly("跨行项目", "45.67", "第一行\n第二行");
    }

    @Test
    void parseHeadersShouldReturnSafeNamesAndSampleTypes() throws Exception {
        String csv = "Project Name,Cost,Cost\nAlpha,88,99\n";

        var columns = service.parseHeaders(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(columns).extracting("name").containsExactly("project_name", "cost", "cost_2");
        assertThat(columns).extracting("type").containsExactly("string", "long", "long");
    }
}
