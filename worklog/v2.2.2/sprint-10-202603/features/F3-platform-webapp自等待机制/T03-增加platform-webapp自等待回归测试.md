# T03: 增加 platform-webapp 自等待回归测试

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标
把 `platform-webapp` 的延迟依赖场景变成可自动回归的测试。

## 技术设计
- 为 entrypoint 编写 shell 级或集成级测试桩
- 模拟 upstream 初始不可解析、稍后可用
- 验证容器行为是“保持存活 + 自动切换”而不是退出

## 影响范围
- `tests/`
- `builds/dts-platform-webapp/`

## 验证
- [ ] 新增测试先失败后通过
- [ ] 回归中覆盖 legacy/normal 关键场景

## 完成标准
- [ ] 该故障场景有自动化保护
