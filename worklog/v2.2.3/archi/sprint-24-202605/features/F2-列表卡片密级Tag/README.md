# F2: 列表卡片密级 Tag

**优先级**: P0
**状态**: READY

## 目标

让大屏列表 / 卡片不打开就能扫到每张大屏的密级，特别是 `null` 裸屏要有显著的视觉警示。运维 / owner / 管理员能一眼判断"这屏密级合规吗"。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 抽离 ClassificationTag 共享组件（颜色映射 + null 警示态） | P0 | READY | - |
| T02 | 大屏列表卡片右上角接入 ClassificationTag | P0 | READY | T01 |

## 完成标准

- [ ] 列表卡片右上角显示密级 Tag：
  - PUBLIC → 灰色「公开」
  - INTERNAL → 蓝色「内部」
  - SECRET → 黄色「秘密」
  - CONFIDENTIAL → 红色「机密」
  - null/缺失 → 橙色「未设密级」+ tooltip「该大屏未设密级，对所有登录用户可见，请联系 owner 补登」
- [ ] Tag 字号与卡片其它信息和谐（不喧宾夺主）。
- [ ] 列表已支持的现有筛选不破坏；可后续加密级筛选作为增量。

## 关键文件

- 新增：`source/dts-platform-webapp/src/analytics/pages/screens/components/ClassificationTag.tsx`
- 改：大屏列表页（screens 列表入口对应文件，预计 `pages/screens/index.tsx` 或类似）

## 注意

- 颜色与 antd 默认 Tag preset 对齐，不引入新色板。
- ClassificationTag 与 F1 ClassificationSelect 是两个组件（不复用），select 用于编辑、tag 用于展示。
- 后端 `toListResponse` 已经在 `ScreenResource.java:1415` 回吐 `classification` 字段，前端直接消费即可，**不需后端改动**。
