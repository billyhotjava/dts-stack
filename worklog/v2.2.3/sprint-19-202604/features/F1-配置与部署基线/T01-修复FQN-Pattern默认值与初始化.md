# T01: 修复 FQN Pattern 默认值与初始化

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

修复 `init.sh` 中 OpenMetadata 表 FQN pattern 默认值被 Bash 参数展开截断的问题。

## 范围

- 修正 `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN` 默认值生成方式。
- 覆盖 `{service}.{database}.{schema}.{table}` 与 `{service}.{database}.{table}` 两类常见 pattern。
- 保持现有 `.env` 写入流程和变量名兼容。
- 增加最小 shell 或文档化验证步骤。

## 完成标准

- [ ] 新初始化环境不会生成 `{service`。
- [ ] pattern 中的 `{service}`、`{database}`、`{schema}`、`{table}` 占位符完整保留。
- [ ] 修改不影响其他 `init.sh` 默认变量。
- [ ] `it/README.md` 记录验证命令和结果。
