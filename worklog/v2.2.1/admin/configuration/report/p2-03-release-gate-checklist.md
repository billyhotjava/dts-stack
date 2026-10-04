# P2-03 发布门禁与回滚清单

## 发布门禁（必须全部满足）

- Liquibase 可解析并成功执行。
- `ops-config` 页面可展示 `scope/restartRequired/validationRule/owner`。
- `BOOTSTRAP` 配置编辑被阻断。
- `RUNTIME_RESTART` 配置编辑有重启提示。
- 集成配置测试结果可见状态码/响应体摘要。
- 漂移检测脚本可产出报告。

## 回归命令

```bash
# 后端编译
cd source/dts-admin && mvn -DskipTests compile

# 前端构建
pnpm -C source/dts-admin-webapp build

# 生成 env 台账
./worklog/v2.2.1/admin/configuration/scripts/build-env-catalog.sh
```

## 快速回滚

1. 回滚应用镜像到上一版本。
2. 若需回滚数据层，执行:
   - 禁用新 UI 使用新增元数据字段（前端降级）
   - 保留新增列，不做 destructive 回滚
3. 恢复 `.env` 权威模式并重启服务。
