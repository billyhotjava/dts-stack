# T01: ScreenResource Optional.get() 全量替换

**严重度**: Critical
**文件**: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java`
**关联**: `ScreenCollaborationResource.java`（同分支修改过，也有 .get() 违规）

## 问题

CLAUDE.md 规定：`Optional.get()` 禁止使用，必须用 `orElseThrow()`。`modernizer-maven-plugin` 会在构建时拒绝。
ScreenResource.java 中约 40+ 处 `user.get()` 违规。
ScreenCollaborationResource.java 中也有同类问题。

## 修复方案

全局替换：
```java
// 替换所有
user.get()  →  user.orElseThrow()
```

注意：部分方法已正确使用 `orElseThrow()`（grant 管理端点），不需要修改。

## 验证

```bash
cd source/dts-analytics
grep -rn '\.get()' src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java | grep -v 'orElseThrow\|getState\|getClass\|getName\|getId\|getScreen\|getUser\|getRole\|getSubject\|getDept\|getPermission\|getClassification\|getMessage\|getBody\|getStatus'
# 应返回 0 结果

mvn compile -pl . -q
# 应无 modernizer 错误
```
