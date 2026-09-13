# T03: 编码边界与不新增页面 Gate

**优先级**: P0  
**状态**: READY  
**依赖**: T02

## 目标

建立 Sprint-51 后续实施 gate：不新增 `/v2` 路由，不复制 v2.2.4 UI 骨架，先改现有页面。

## 技术设计

后续 PR 必须检查：

- `src/routes/sections/index.tsx` 不新增 `v2Routes`
- 不新增 `src/v2/`
- 菜单 seed 不新增同义页面入口
- 新组件必须服务现有页面或现有详情抽屉
- 后端补接口必须来自 `assets/api-gap-register.md`

## 影响范围

- Sprint-51 后续实施规范

## 验证

- [ ] source-contract 增加“未新增 `/v2` 路由”断言或 review checklist。
- [ ] Git diff 中无新菜单入口，除非任务明确批准。

## 完成标准

- [ ] 编码前 gate 可执行、可检查。
