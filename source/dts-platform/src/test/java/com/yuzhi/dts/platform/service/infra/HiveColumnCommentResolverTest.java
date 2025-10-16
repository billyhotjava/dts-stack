package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HiveColumnCommentResolverTest {

    @Test
    void parseColumnCommentsExtractsInlineComments() {
        String ddl = """
            CREATE TABLE `ods_user_logs`(
              `log_id` string COMMENT '日志ID',
              `price` decimal(10,2) COMMENT '商品价格',
              `quantity` int COMMENT '数量',
              `note` string
            )
            STORED AS TEXTFILE
            """;

        Map<String, String> comments = HiveColumnCommentResolver.parseColumnComments(ddl);

        assertThat(comments)
            .containsEntry("log_id", "日志ID")
            .containsEntry("price", "商品价格")
            .containsEntry("quantity", "数量")
            .doesNotContainKey("note");
    }

    @Test
    void parseColumnCommentsStopsAtPartitionSection() {
        String ddl = """
            CREATE TABLE demo_table(
              id string COMMENT 'primary id'
            )
            PARTITIONED BY (`day` string COMMENT 'partition day')
            STORED AS PARQUET
            """;

        Map<String, String> comments = HiveColumnCommentResolver.parseColumnComments(ddl);

        assertThat(comments)
            .containsEntry("id", "primary id")
            .doesNotContainKey("day");
    }

    @Test
    void normalizeCommentFiltersNullLikeValuesAndUnescapesQuotes() {
        assertThat(HiveColumnCommentResolver.normalizeComment("null")).isNull();
        assertThat(HiveColumnCommentResolver.normalizeComment("\\N")).isNull();
        assertThat(HiveColumnCommentResolver.normalizeComment("  ")).isNull();
        assertThat(HiveColumnCommentResolver.normalizeComment("can''t be null")).isEqualTo("can't be null");
    }
}
