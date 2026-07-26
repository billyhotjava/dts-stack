# GitNexus 变更影响

统一验证阶段执行过一次 `gitnexus_detect_changes(scope=all)`：

- 88 个文件
- 720 个 changed symbols
- 74 个 affected symbols/processes
- 总体风险：`CRITICAL`

风险来自 Sprint 跨越接入、密级事实、传播、生命周期、查询/导出/分享、指标、API/数据产品和大屏，
与既定 F1～F8 范围一致，但影响面较大。已完成相应定向测试、生产包和部分真实浏览器验证；尚缺的外部
集成、完整权限矩阵和销毁安全证据仍作为 No-Go 条件保留。
