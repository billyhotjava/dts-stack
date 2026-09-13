# T03: IT 证据记录规范

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

明确每个后续 UI task 如何记录验证证据。

## 技术设计

每个实现型 task 至少记录：

- RED 测试命令和失败原因
- GREEN 测试命令和通过结果
- `pnpm build` 或模块构建结果
- 浏览器路由、截图路径、console/network 结果
- 不能运行的验证项和原因

## 影响范围

- `worklog/v2.2.3/sprint-48-202606/it/README.md`

## 验证

- [x] 当前 sprint 已记录 skill 验证、菜单抽取和未运行构建原因

## 完成标准

- [x] 后续 sprint 的 IT 证据有统一格式
