# Sprint-20 集成验证

## 验证目标

验证逻辑建模工作区中的企业级文件浏览器和统一批量删除能力可用，且不会破坏现有单模型编辑流。

## 场景清单

- [x] 左树目录节点支持 checkbox、半选、全选、取消选择
- [x] 点击模型名只切换右侧编辑器，不改变批量选择状态
- [x] 左树勾选多个模型后，可从左侧或顶部触发批量删除
- [x] 治理弹窗勾选模型后，可复用同一批量删除链路
- [x] 平铺列表勾选模型后，可复用同一批量删除链路
- [x] 批量删除成功后，左树、列表、治理结果、编辑区状态同步刷新
- [x] 部分删除失败时，页面能展示失败明细，不会整批静默失败

## 已执行验证

- [x] `node --experimental-strip-types --test source/dts-platform-webapp/src/pages/modeling/sqlModelBulkSelection.helpers.test.ts`
- [x] `node --experimental-strip-types --test source/dts-platform-webapp/src/pages/modeling/sqlModelDeleteFlow.helpers.test.ts`
- [x] `node --experimental-strip-types --test source/dts-platform-webapp/src/pages/modeling/sqlModelBatchDeleteResult.helpers.test.ts`
- [x] `./mvnw -Dtest=ModelingSqlModelServiceBatchDeleteTest test` in `source/dts-platform`
- [x] `pnpm -C source/dts-platform-webapp build`

## 待人工回归

- [ ] `逻辑建模` 工作区：左树多选、治理弹窗多选、未归档列表删除、删除结果弹窗
