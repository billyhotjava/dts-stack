# T01: source-contract TDD 入口

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

定义后续页面整改的测试优先入口。

## 技术设计

适合 source-contract 的场景：

- 路由必须存在或必须重定向
- 菜单标题不能重复或不能出现旧名称
- 页面不能包含客户 demo 场景
- 按钮必须有 disabled reason
- 表格列必须有固定宽度或 nowrap 规则
- 可选后端接口失败不能弹全局错误

## 影响范围

- `assets/dts-frontend-refactor-rules.md`
- `assets/button-component-matrix.md`

## 验证

- [x] sprint 规则明确写入 TDD gate

## 完成标准

- [x] 后续 UI 代码任务可直接按 RED-GREEN-REFACTOR 执行
