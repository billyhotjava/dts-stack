# Sprint-57 集成验证

**状态**: 未开始

## 验证项

| # | 场景 | 方式 | 证据 | 状态 |
|---|------|------|------|------|
| IT-1 | 模板 zip 下载 → 填写 → preview 校验报告（含故意错误行） | curl + 页面 | 报文 + 截图 | ☐ |
| IT-2 | apply 全流程入库，五类实体计数正确 | curl + SQL 计数 | 报文 + SQL 输出 | ☐ |
| IT-3 | rollback 后计数还原；被篡改实体 SKIPPED | curl + SQL | 报文 | ☐ |
| IT-4 | 旧数据元直导端点兼容 + 出现在 runs 历史 | curl | 报文 | ☐ |
| IT-5 | 内置国标包一键安装（gbt-2261/4658/2260 + 数据元包），重复安装幂等 | 页面 | 截图 | ☐ |
| IT-6 | 数据元 code_set 与已安装码表关联正确 | 页面 + SQL | 截图 | ☐ |
| IT-7 | 菜单/直达 URL 可访问，不落 /workbench 兜底 | 页面 | 截图 | ☐ |
| IT-8 | 三页面范式巡检记录 | 文档 | assets/foundation-pages-audit.md | ☐ |
| IT-9 | 既有 source-contract 测试全绿（模板 zip 契约不破坏） | node --test | 命令输出 | ☐ |

证据存放：`../assets/`（截图命名 `it-{N}-{描述}.png`）。
