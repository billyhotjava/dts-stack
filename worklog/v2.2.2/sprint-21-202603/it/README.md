# Sprint-21 集成测试

## 测试清单

### F1: 门户与外壳统一
- [ ] 登录后默认进入 `/portal` 门户页
- [ ] 门户页显示"BI分析"和"大数据平台"双卡片
- [ ] 点击"BI分析"跳转到 `/analytics`
- [ ] 点击"大数据平台"跳转到 `/dashboard/workbench`
- [ ] 勾选"记住选择"后，下次登录自动跳转
- [ ] 不勾选时每次都显示门户页
- [ ] analytics-webapp 使用 antd Header
- [ ] platform-webapp Header 有"切换应用"按钮
- [ ] analytics-webapp Header 有"切换应用"按钮
- [ ] 切换按钮正确跳转到另一个 app

### F2: 业务组件迁移
- [ ] 所有页面正常渲染（无自定义 UI 残留）
- [ ] Button 交互正常（click、disabled、loading）
- [ ] Modal 打开/关闭正常
- [ ] Select 下拉选择正常
- [ ] Spin 加载状态不破坏布局
- [ ] Card 卡片布局正常
- [ ] `ui/` 目录已删除
- [ ] 生产构建通过

### F3: 侧边导航统一
- [ ] analytics-webapp 侧边栏使用 antd Menu
- [ ] 所有菜单项路由跳转正常
- [ ] 侧边栏折叠/展开正常
- [ ] 折叠时只显示图标
- [ ] 视觉风格与 platform-webapp 一致

### 跨功能测试
- [ ] 完整流程：登录 → 门户 → BI分析 → 切换到专业版 → 切换回BI
- [ ] Session 保持：在两个 app 间切换不会丢失登录状态
- [ ] Chrome 95 兼容性验证
- [ ] 离线环境：所有 antd 资源本地打包，无 CDN 请求
