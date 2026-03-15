# Sprint-8 集成测试说明

本 Sprint 的验证重点不是“继续扩展设计器能力边界”，而是确认现有大屏设计器已经具备更稳定的产品化闭环。

## 验证层次

### 1. 后端集成测试

重点验证：

- `/api/marketplace/components` 与 `/api/marketplace/templates` 不再是 404
- `screen-templates` 的 listing / restore / create-screen 路径可回归
- `screen-packs` 的 import / export / ops health / runtime probe 关键路径可回归
- `screen-plugins` 不再只返回 demo plugin

建议命令：

```bash
cd source/dts-analytics
./mvnw test
```

### 2. 前端测试与构建

重点验证：

- `TemplateGallery`
- `ScreenMarketplacePage`
- `ScreenHeader` 关键动作

建议命令：

```bash
pnpm -C source/dts-analytics-webapp/modern test
pnpm -C source/dts-analytics-webapp/modern build
```

### 3. Web 自动化验证

建议把以下场景纳入 analytics 侧 smoke：

- screens 列表加载
- 通过模板创建大屏
- 模板资产中心打开与基础筛选
- marketplace 打开与安装动作
- analytics 页面入口可访问

建议命令：

```bash
python3 tests/run_suite.py --suite web-e2e-core --fail-fast
python3 tests/run_suite.py --suite web-e2e-full --fail-fast
```

### 4. 人工走查

需要确认：

- marketplace 页面不再出现“敬请期待”式占位感
- 模板/行业包流程不再依赖原始 `prompt/alert`
- 插件安装后的组件在组件库中可见
- 失败态、空态、加载态都有明确反馈

## 完成标准

- `sprint-8` README 中 8 个 task 全部有明确交付
- marketplace 前后端链路真实存在
- 模板与行业包高频流程完成产品化收口
- 大屏设计器关键资产链路具备最基本的自动化回归
