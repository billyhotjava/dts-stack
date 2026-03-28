# T04: ScreenHeader 导入导出菜单重构

**优先级**: P0
**状态**: READY
**依赖**: T01, T02, T03

## 目标

在 ScreenHeader 中新增"导入导出"一级 HeaderMenu，聚合所有导入导出操作，简化操作路径。

## 技术设计

文件: `pages/screens/components/ScreenHeader.tsx`

**新增 HeaderMenu "导入导出":**

```
[画布]  [工具箱]  [导入导出]  [版本]  [治理]    [预览] [发布] [保存]
```

菜单内部分两个 section：

```
导入
  [选择文件...] 导入 JSON

导出
  预览设备: [自动 ▾]
  [导出 JSON]  含内联资源
  [导出 PNG]
  [导出 PDF]
```

**迁移:**
- 原"版本导出"菜单（`tools-release`）中的导出动作（PNG/PDF/JSON）移到新菜单
- 原"版本导出"改名为"版本"，只保留版本历史/回滚/对比 + 预览设备选择
- 原"工具箱"菜单（`tools-main`）中的设计动作 `import` 选项移除

**JSON 导出改动:**
- 调用 `inlineResources(spec)` 后再构建导出 payload
- 新增 `resourcesInlined: true` 字段

## 影响范围

- 修改: `ScreenHeader.tsx` — HeaderMenu 结构、菜单项迁移
- 导出逻辑改用 resourceInliner

## 验证
- [ ] 新"导入导出"菜单正确显示导入和导出区域
- [ ] 原"版本导出"菜单不再包含导出动作
- [ ] 原"工具箱"菜单不再包含"导入JSON"设计动作
- [ ] 导出 JSON 按钮一键触发（不需要先选格式再执行）
- [ ] 导出 PNG/PDF 按钮一键触发

## 实施步骤

> 详见 `implementation-plan.md` Task 4

- [ ] 添加 `inlineResources` import
- [ ] 在"主题"菜单之后插入新的"导入导出" HeaderMenu (`tools-io`)
- [ ] 将"版本导出"菜单改名为"版本"，移除导出相关项
- [ ] 移除"编辑"菜单中的"导入JSON"按钮
- [ ] 增强 `handleExportJson` 调用 `inlineResources` 内联资源
- [ ] TypeScript 编译验证通过
- [ ] 提交: `feat(F1/T04): add consolidated import/export menu, inline resources on JSON export`

## 完成标准
- [ ] 导入导出入口从 4 步简化为 2 步
- [ ] 菜单结构清晰，导入导出操作集中
