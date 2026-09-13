# T02: 端到端 smoke 与证据归档

**优先级**: P0  
**状态**: READY  
**依赖**: F2, F3

## 目标

沉淀正式版端到端验证脚本、命令输出和证据路径。

## 技术设计

验证链路：

1. 上传运营商 CSV 到 DTS。
2. DTS 预检和 ODS 入湖。
3. 导入/运行 thales dbt 模型。
4. 调用 DTS snapshot export API。
5. metro-stack 校验 snapshot package。
6. metro-stack 创建训练任务并跑到产物摘要。

## 影响范围

- `worklog/v2.2.3/sprint-30-202605/it/README.md`
- `worklog/v2.2.3/sprint-30-202605/it/evidence/`

## 验证

- [ ] 记录每一步命令/API、HTTP 状态、输出路径。
- [ ] 记录失败时的排查入口。

## 完成标准

- [ ] 端到端 smoke 可由其他工程师复现。
