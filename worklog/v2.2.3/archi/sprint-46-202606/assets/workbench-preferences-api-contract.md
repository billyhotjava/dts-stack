# 工作台个人偏好 API 契约

## 目标

服务端按当前登录用户保存工作台首页组件的勾选状态和显示顺序。前端不能通过请求体指定其他用户。

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/workbench/preferences` | 获取当前登录用户配置；无配置时返回角色默认模板 |
| `PUT` | `/api/workbench/preferences` | 保存当前登录用户配置 |
| `POST` | `/api/workbench/preferences/reset` | 恢复角色默认模板 |

## GET 响应

```json
{
  "version": 1,
  "availableComponents": [
    {
      "key": "todo",
      "title": "待办事项",
      "description": "查看审批、阻断和异常处理项",
      "enabled": true,
      "disabledReason": null
    }
  ],
  "items": [
    { "key": "todo", "visible": true, "order": 10 }
  ]
}
```

## PUT 请求

```json
{
  "items": [
    { "key": "todo", "visible": true, "order": 10 },
    { "key": "golden-chain", "visible": true, "order": 20 },
    { "key": "core-assets", "visible": false, "order": 30 }
  ]
}
```

## 校验规则

- `items` 不能为空数组时允许保存，表示用户临时清空首页。
- `key` 必须存在于后端组件注册表。
- `order` 必须为非负整数。
- 同一个 `key` 不能重复。
- 无权限组件不能保存为 `visible=true`。
- 后端返回前必须过滤当前用户无权限组件。

## 持久化建议

表名：`workbench_user_preference`

| 字段 | 类型建议 | 说明 |
|------|----------|------|
| `id` | bigint/uuid | 主键 |
| `username` | varchar(128) | 登录账号，唯一 |
| `version` | int | 配置版本 |
| `layout_json` | text/jsonb | JSON 配置 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

## 审计

- 保存偏好：`WORKBENCH_PREFERENCE_SAVE`
- 恢复默认：`WORKBENCH_PREFERENCE_RESET`
- 拒绝未知组件：记录安全审计或 warn 日志
