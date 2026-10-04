# T03: companion 文件写入范围与回滚规则收口

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标

防止导入过程中把未成功导入模型的 companion 文件污染到 workspace。

## 技术设计

- 收口写文件边界
- 明确失败时的回滚策略

## 影响范围

- `ModelFileService.java`
- `ModelGenerationService.java`

## 验证

- [ ] 文件写入边界测试

## 完成标准

- [ ] 未成功模型不会污染工作区
