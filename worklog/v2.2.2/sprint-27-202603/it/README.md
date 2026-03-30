# Sprint-27 IT / 验收清单

## 自动化验证

- [x] 财务插件注册测试通过
- [x] 财务模板定义测试通过
- [x] 财务插件基础契约测试通过
- [ ] 财务预览运行态专项测试通过
- [x] `source/dts-analytics-webapp/modern` `pnpm typecheck` 通过
- [x] `source/dts-analytics-webapp/modern` `pnpm build` 通过

### 计划执行命令

- `cd source/dts-analytics-webapp/modern && node --import tsx --test src/pages/screens/plugins/custom/financePluginAdapters.test.ts src/pages/screens/financeTemplates.test.ts src/pages/screens/componentLibraryPlugins.test.ts`
- `cd source/dts-analytics-webapp/modern && pnpm typecheck`
- `cd source/dts-analytics-webapp/modern && pnpm build`

## 人工回归

- [ ] 模板库中可看到四套财务大屏模板
- [ ] 以模板创建的新屏在设计器中可正常打开
- [ ] 财务组件可独立拖拽、缩放、删除
- [ ] 财务组件属性修改后可保存并再次打开保持一致
- [ ] 预览页视觉风格与需求原型主方向一致
- [ ] 公共运行页不因财务插件崩溃
- [ ] 非财务模板不受影响
