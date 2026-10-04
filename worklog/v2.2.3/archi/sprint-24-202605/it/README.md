# Sprint-24 集成测试与验收

## 端到端验收清单

### IT-1: 创建大屏强制设密
1. 普通 owner 用户登录 dts-platform-webapp。
2. 进大屏列表 → 点「新建」。
3. 不选密级直接点「确定」→ 期望按钮 disabled 或弹错误「请选择密级」。
4. 选 INTERNAL → 创建成功。
5. 列表卡片立即显示「内部」Tag。
6. 调 API `GET /bi/api/screens/{newId}` → response 含 `classification: "INTERNAL"`。
7. 直接 curl `POST /bi/api/screens` body 不带 classification → HTTP 400 + error 消息明确。

### IT-2: 编辑器属性面板可改密级
1. 打开自己创建的大屏。
2. 右侧属性面板「基础信息」顶部能看到「大屏密级」label + select。
3. 切换密级 → 弹「已更新为 X」message。
4. 关闭刷新 → 密级值持久化。
5. 用别人的账号（非 owner、非 manager）打开 → 显示 Tag（只读），无 select。

### IT-3: 列表卡片密级 Tag
1. 列表中现有大屏：每张卡片右上角能看到密级 Tag，颜色与密级对应。
2. 老裸屏（如生产 id=60）显示橙色「未设密级」+ tooltip。

### IT-4: 裸屏盘点
1. OP_ADMIN 登录 → 进管理页 → 打开「裸屏盘点」面板。
2. 看到所有 `classification IS NULL` 的大屏列表，含 creator 信息。
3. 点击某行的「去补登」按钮 → 跳转编辑器，属性面板密级 select 显示「未设密级」placeholder。
4. 改完密级回到盘点列表 → 该屏从列表消失。
5. 普通用户调同一端点 → 403。
6. dts-admin 审计后台能查到 `screen.compliance.audit_unclassified` 记录。

### IT-5（可选 F5）: 降级二次确认
1. owner 把大屏从 SECRET 降到 INTERNAL → 弹 confirm modal 要求填原因。
2. 不填原因 → 「确认」disabled。
3. 填原因 → 提交后审计 payload 含 `before / after / reason` 三字段。
4. 升级路径（INTERNAL → SECRET）不弹 modal，直接生效。

## 集成测试位置

- 后端：`source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceCreateClassificationTest.java`
- 后端：`source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceUnclassifiedAuditTest.java`
- 前端 e2e：`tests/web-e2e/specs/dashboard-classification-ux.spec.ts`（如有 e2e 框架）

## 上线前数据评估（必做）

```sql
-- 评估存量裸屏数量
SELECT COUNT(*) AS unclassified_count
FROM analytics_screen
WHERE archived = false AND classification IS NULL;

-- 列出 owner，便于通知补登
SELECT s.id, s.name, u.email AS creator_email, s.created_at
FROM analytics_screen s
LEFT JOIN analytics_user u ON u.id = s.creator_id
WHERE s.archived = false AND s.classification IS NULL
ORDER BY s.created_at DESC;
```

把结果留 evidence/ 里，作为本 sprint 的合规盘点输入。

## 上线后回归

- F3 上线 24h 内：验证没有新增 `classification IS NULL` 大屏（每日跑一次上面 SQL）。
- F4 上线 1 周内：跟运营核对裸屏处理进度，目标降到 0。
