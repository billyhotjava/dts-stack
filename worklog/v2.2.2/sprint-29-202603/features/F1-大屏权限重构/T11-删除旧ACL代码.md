# T11: 删除 ScreenAclService/Repository/Domain

**优先级**: P1
**状态**: READY
**依赖**: T04

## 目标
删除 analytics 中所有 screen ACL 相关的 Java 代码

## 技术设计

删除文件：
- `service/ScreenAclService.java`
- `repository/AnalyticsScreenAclRepository.java`
- `domain/AnalyticsScreenAcl.java`

确认无其他文件引用这三个类（grep 验证）。

## 影响范围
- 删除: 3 个 Java 文件
- 验证: 全局搜索确认无残留引用

## 验证
- [ ] 编译通过
- [ ] grep 无 ScreenAclService/AnalyticsScreenAcl 引用

## 完成标准
- [ ] 三个文件已删除
- [ ] 编译无错误
