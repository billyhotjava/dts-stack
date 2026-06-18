# T03: 代码 review 与提交收口

**状态**: DONE
**优先级**: P0

## Review 结论

未发现阻断问题。

## 复核点

- 连接器目录：表格列宽、能力标签、操作按钮、配置 Drawer、创建数据源交接均有 source-contract 和浏览器证据。
- 资产消费：资产台账操作列固定，数据产品“查看消费”默认进入唯一工作台消费发布 section，历史 `/services/consumption` 查询上下文保留。
- 工作台入口：7 个首页组件动作均有真实路由落点，自定义工作台采用复选框和上移/下移，默认本地偏好降级避免未升级后端接口噪音。
- Chrome95：未引入不兼容 CSS/JS 能力；布局使用固定宽度、横向滚动和 nowrap 约束。

## 剩余风险

- 既有全局导航存在 React `li` 嵌套 `li` warning：`src/components/nav/vertical/nav-list.tsx`。本 Sprint smoke 已记录并过滤该已知非本页面 warning，建议后续单独修复。
- `pnpm build` 仍提示 Browserslist 数据过期和部分 chunk 超过 1500 kB，属于既有构建告警。

## 提交记录

- `1054ff955 feat(F1): close sprint 49 data access flow`
- `6caa1a357 feat(F2): close asset consumption flow`
- `0d242d1ea feat(F3): close workbench entry flow`
