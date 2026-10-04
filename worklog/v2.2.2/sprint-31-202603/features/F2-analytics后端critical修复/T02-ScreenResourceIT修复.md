# T02: ScreenResourceIT 测试修复

**严重度**: Critical
**文件**: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceIT.java`

## 问题

测试仍引用已删除的：
- `AnalyticsScreenAcl` 实体类
- `AnalyticsScreenAclRepository` 仓库
- `GET /api/screens/{id}/acl` 端点（已替换为 `/api/screens/{id}/grants`）

Liquibase `0041_drop_screen_acl.xml` 已删除 `analytics_screen_acl` 表，测试无法运行。

## 修复方案

1. 移除 `AnalyticsScreenAcl` 和 `AnalyticsScreenAclRepository` 的 import 和使用
2. 移除测试 ACL 端点的测试方法
3. 新增测试 grants 端点的测试方法（对应新的 `PUT /api/screens/{id}/grants` 和 `DELETE /api/screens/{id}/grants`）
4. 确认测试编译通过

## 验证

```bash
cd source/dts-analytics
mvn test-compile -pl . -q
# 应无编译错误
```
