package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DbtSqlProjectionParserTest {

    @Test
    void keepsColumnQualifiersAndResolvesTheirPhysicalRelations() {
        String sql = """
            select a.amount + b.amount as total_amount,
                   a.id as order_id
              from warehouse.sales.orders as a
              join warehouse.sales.refunds b on b.order_id = a.id
            """;

        var expressions = DbtSqlProjectionParser.selectExpressionsByAlias(sql);
        var amount = DbtSqlProjectionParser.columnReference(expressions.get("total_amount"), "amount");
        var orderId = DbtSqlProjectionParser.columnReference(expressions.get("order_id"), "id");

        assertThat(amount.qualifiers()).containsExactlyInAnyOrder("a", "b");
        assertThat(amount.unqualified()).isFalse();
        assertThat(orderId.qualifiers()).containsExactly("a");
        assertThat(DbtSqlProjectionParser.relationAliases(sql))
            .containsEntry("a", "orders")
            .containsEntry("b", "refunds");
    }

    @Test
    void distinguishesAnUnqualifiedReferenceFromAQualifiedOne() {
        var reference = DbtSqlProjectionParser.columnReference("coalesce(amount, 0) as amount", "amount");

        assertThat(reference.qualifiers()).isEmpty();
        assertThat(reference.unqualified()).isTrue();
        assertThat(reference.referenced()).isTrue();
    }

    @Test
    void candidatePublicationRoutesQualifiedColumnsAndSuppressesAmbiguousFallbacks() throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/CandidatePublicationRepository.java")
        );

        assertThat(source)
            .contains("DbtSqlProjectionParser.relationAliases(rootSql)")
            .contains("DbtSqlProjectionParser.columnReference(")
            .contains("reference.qualifiers().stream().anyMatch(allowedQualifiers::contains)")
            .contains("matchPhysicalSourceColumns(")
            .contains("allowedQualifiers,\n                columnOwnerCounts")
            .contains("columnOwnerCounts.getOrDefault(columnKey, 0) == 1")
            .contains("sameName != null &&")
            .contains("uniquelyOwned &&");
    }
}
