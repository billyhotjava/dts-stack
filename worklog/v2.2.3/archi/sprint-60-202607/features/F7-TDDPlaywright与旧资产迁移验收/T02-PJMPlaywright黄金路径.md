# T02: PJM Playwright 黄金路径

**优先级**: P0
**状态**: IN_PROGRESS（PJM 台账部署验收已通过，普通提交/运行与高级 dbt 页面仍待环境级验收）
**依赖**: T01,F6

## 目标

使用 PJM fixture 在浏览器中验证普通建模和高级 dbt 两条路径都能回到同一模型台账。

## 技术设计

普通路径：低代码向导 → 业务过程 → 项目节点对象 → 标准绑定 → ModelSpec → 编译 → 提交运行。

高级路径：模型台账 → dbt 导入 → SQL 文件 → manifest → 字段/血缘/drift → 运行。

## 影响范围

- `source/dts-platform-webapp/e2e/**`
- Playwright mock/API fixture。
- `it/evidence/playwright/` 截图和 trace。

## 验证

- [x] 已新增可复现的 PJM 业务对象台账 Playwright 场景，使用 route fixture 验证粒度、来源和下一步入口。
- [x] `https://bi.yuzhicloud.com` 真实浏览器运行通过；使用 `portal_session` cookie-only 登录和 `/usr/bin/google-chrome`。

- [ ] 普通用户不填写 SQL 也能提交模型。
- [ ] 高级开发能看到 dbt 文件和漂移状态。
- [ ] DWD/DWS/ADS 分层、模型状态、运行证据可见。

## 完成标准

- [ ] 记录 Chrome 95 兼容截图和关键网络请求。
