package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperationTypeNormalizerTest {

    private final OperationTypeNormalizer normalizer = new OperationTypeNormalizer();

    @ParameterizedTest
    @DisplayName("英文 verb 直接映射")
    @CsvSource({
        "CREATE,CREATE",
        "ADD,CREATE",
        "DELETE,DELETE",
        "UPDATE,UPDATE",
        "MODIFY,UPDATE",
        "EDIT,UPDATE",
        "SAVE,UPDATE",
        "READ,READ",
        "QUERY,READ",
        "GET,READ",
        "EXPORT,EXPORT",
        "IMPORT,IMPORT",
        "GRANT,GRANT",
        "REVOKE,REVOKE",
        "ENABLE,ENABLE",
        "DISABLE,DISABLE",
        "PUBLISH,PUBLISH",
        "ARCHIVE,ARCHIVE",
        "PURGE,CLEAN",
        "CLEAN,CLEAN",
        "EXECUTE,EXECUTE",
        "RUN,EXECUTE",
        "REFRESH,REFRESH",
        "TEST,TEST",
        "LOGIN,LOGIN",
        "LOGOUT,LOGOUT",
        "DOWNLOAD,DOWNLOAD",
        "UPLOAD,UPLOAD",
        "APPROVE,APPROVE",
        "REJECT,REJECT"
    })
    void englishVerbsCanonicalised(String input, String expected) {
        assertThat(normalizer.canonicalOperationType(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("中文 verb 映射")
    @CsvSource({
        "新增数据集,CREATE",
        "删除用户,DELETE",
        "更新菜单,UPDATE",
        "查看审计,READ",
        "导出审计日志,EXPORT",
        "导入数据,IMPORT",
        "授权访问,GRANT",
        "启用账号,ENABLE",
        "禁用账号,DISABLE",
        "登录系统,LOGIN",
        "下载文件,DOWNLOAD",
        "上传文件,UPLOAD",
        "刷新数据,REFRESH"
    })
    void chineseVerbsCanonicalised(String input, String expected) {
        assertThat(normalizer.canonicalOperationType(input)).isEqualTo(expected);
    }

    /**
     * Pre-existing prefix-collision: "撤销授权" contains "授权" so the GRANT branch fires before the REVOKE
     * one; same story for "退出登录" against LOGIN. Capturing as known-behaviour so future RF refactors
     * cannot silently drift the canonicalisation contract; a follow-up task should reorder the rules so
     * the more-specific verb (REVOKE / LOGOUT) wins.
     */
    @ParameterizedTest
    @DisplayName("已知顺序歧义：长 verb 被短 verb 抢先（应在 follow-up 修）")
    @CsvSource({
        "撤销授权,GRANT",
        "退出登录,LOGIN",
        "REMOVE,READ"
    })
    void chineseVerbPrefixCollisionsAreCurrentBehaviour(String input, String currentResult) {
        assertThat(normalizer.canonicalOperationType(input)).isEqualTo(currentResult);
    }

    @Test
    @DisplayName("空 / null → READ 默认")
    void blankDefaultsToRead() {
        assertThat(normalizer.canonicalOperationType(null)).isEqualTo("READ");
        assertThat(normalizer.canonicalOperationType("")).isEqualTo("READ");
        assertThat(normalizer.canonicalOperationType("    ")).isEqualTo("READ");
        assertThat(normalizer.canonicalOperationType("nothing-recognisable")).isEqualTo("READ");
    }

    @Test
    @DisplayName("deriveOperationType 优先取 payload.operationType")
    void deriveOperationTypePrefersPayload() {
        Map<String, Object> payload = Map.of("operationType", "EXPORT");
        assertThat(normalizer.deriveOperationType("CreateUser", payload)).isEqualTo("EXPORT");
    }

    @Test
    @DisplayName("deriveOperationType payload 缺失时回落到 action")
    void deriveOperationTypeFallsBackToAction() {
        assertThat(normalizer.deriveOperationType("DeleteUser", Map.of())).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("deriveOperationType 都缺失时回落到 summary")
    void deriveOperationTypeFallsBackToSummary() {
        Map<String, Object> payload = Map.of("summary", "导出审计日志");
        assertThat(normalizer.deriveOperationType(null, payload)).isEqualTo("EXPORT");
    }

    @Test
    @DisplayName("操作类型 token 大小写敏感容错")
    void caseInsensitive() {
        assertThat(normalizer.canonicalOperationType("create")).isEqualTo("CREATE");
        assertThat(normalizer.canonicalOperationType("CREATE_USER")).isEqualTo("CREATE");
        assertThat(normalizer.canonicalOperationType("UserCreate")).isEqualTo("CREATE");
    }
}
