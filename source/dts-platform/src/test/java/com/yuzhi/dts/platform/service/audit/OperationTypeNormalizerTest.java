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
    @DisplayName("中文 verb 映射（含 BUG-A 修复后的长 verb 优先）")
    @CsvSource({
        "新增数据集,CREATE",
        "删除用户,DELETE",
        "更新菜单,UPDATE",
        "查看审计,READ",
        "导出审计日志,EXPORT",
        "导入数据,IMPORT",
        "授权访问,GRANT",
        "撤销授权,REVOKE",
        "取消授权,REVOKE",
        "启用账号,ENABLE",
        "禁用账号,DISABLE",
        "登录系统,LOGIN",
        "退出登录,LOGOUT",
        "注销登录,LOGOUT",
        "下载文件,DOWNLOAD",
        "上传文件,UPLOAD",
        "刷新数据,REFRESH"
    })
    void chineseVerbsCanonicalised(String input, String expected) {
        assertThat(normalizer.canonicalOperationType(input)).isEqualTo(expected);
    }

    /** REMOVE 仍未在英文规则里映射到 DELETE — 单独标记。 */
    @ParameterizedTest
    @DisplayName("已知遗留：英文 REMOVE 没有 DELETE 别名（如有需要可下次补）")
    @CsvSource({
        "REMOVE,READ"
    })
    void englishRemoveStillFallsThroughToRead(String input, String currentResult) {
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
