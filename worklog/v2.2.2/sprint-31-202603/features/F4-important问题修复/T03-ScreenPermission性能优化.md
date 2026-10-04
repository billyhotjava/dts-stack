# T03: ScreenPermissionService 性能优化

**模块**: Analytics 后端
**文件**: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenPermissionService.java`

## 问题

`listAccessibleScreenIds()` 对非管理员用户加载所有未归档 screen 到内存再 filter。大数据量时性能差。

## 修复方案

新增 Repository 方法，用 SQL 过滤：
```java
// AnalyticsScreenRepository
List<AnalyticsScreen> findAllByCreatorIdAndArchivedFalse(Long creatorId);
```

替换内存过滤：
```java
// 替换
screenRepository.findAllByArchivedFalseOrderByIdDesc().stream().filter(s -> isCreator(s, user))
// 为
screenRepository.findAllByCreatorIdAndArchivedFalse(user.getId())
```
