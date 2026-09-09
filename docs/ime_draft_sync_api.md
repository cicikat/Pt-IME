# JadeBoard 三小时草稿增量回传接口

本文档对应客户端 `DraftSyncRepository` 的当前实现。它发送的是**已脱敏的新增草稿记录**，不是旧设计中的日报聚合 payload。

## 客户端配置

- 回传默认关闭。
- 地址必须是 `https://`，设置项保存完整 URL，例如 `https://ime.lan.example/v1/ime/drafts`。
- 使用 `Authorization: Bearer <配对密钥>`。
- 默认发送间隔为 15 分钟，当前客户端在写入新记录后也会立即尝试发送；后端无需依赖定时请求。

## 请求

```http
POST /v1/ime/drafts HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```

请求体是 JSON 数组，按本地数据库 ID 升序排列：

```json
[
  {
    "id": 123,
    "created_at": 1755000000000,
    "app_package": "com.tencent.mm",
    "source": "keyboard",
    "content": "今天买了***个苹果"
  },
  {
    "id": 124,
    "created_at": 1755000010000,
    "app_package": "com.tencent.mm",
    "source": "voice",
    "content": "明天上午开会"
  }
]
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | integer | 手机端单调递增的本地记录 ID，服务端幂等键 |
| `created_at` | integer | Unix epoch milliseconds |
| `app_package` | string | 当前编辑器包名 |
| `source` | string | `keyboard` 或 `voice` |
| `content` | string | 已脱敏文本；ASCII/全角阿拉伯数字已替换为 `*` |

中文数字不会被替换。复制、剪切、粘贴不会进入该接口。

## 响应

客户端把任意 `2xx` 视为成功，并将本批次最后一个 `id` 标记为已发送；非 `2xx` 会保留待下次重试。因此服务端应在**整批成功持久化后**返回 `200` 或 `204`，失败则返回 `4xx/5xx`。

推荐响应：

```http
HTTP/1.1 204 No Content
```

服务端必须按 `(device_id, id)` 去重，重复请求返回 `2xx`，不能重复产生业务记录。客户端可能因断网重试整批。

## 后端必须完成的工作

1. 新增 HTTPS `POST` 接口（建议路径 `/v1/ime/drafts`；客户端设置项填写完整地址）。
2. 校验 Bearer 配对密钥、设备权限和请求 JSON schema。
3. 限制请求体大小、数组长度和单条 `content` 长度。
4. 仅保存客户端已脱敏字段；服务端日志、异常日志和 tracing 禁止打印 `content`。
5. 以 `(device_id, id)` 做幂等去重。
6. 密钥撤销后返回 `401` 或 `403`；客户端不会推进本地游标。
7. 将数据写入后端自己的三小时存储，并按 `created_at` 清理超过三小时的数据（若需要保留审计，必须另行取得授权）。

## 与旧 `/sensor/ime` 日报接口的关系

`DESIGN.md`/`PLAN.md` 中的 `/sensor/ime` 是另一种“每日聚合统计”协议，字段包括 `keys`、`chars`、`hours` 等；当前客户端草稿回传**没有发送这些字段**。如果 Emerald-presence 仍要沿用 `/sensor/ime`，可以把本接口路径配置为 `https://.../sensor/ime`，但服务端必须按本文档接收 JSON 数组，不能按日报对象解析。

后续若需要日报统计，应新增独立接口或版本化协议，避免把草稿记录和行为统计混在同一个 schema 中。

## 隐私边界

- 密码、邮箱、支付等敏感输入框不写入草稿库，也不发送。
- 锁屏或无焦点编辑器不启动语音、不写入草稿。
- 语音实际提交给输入框的是完整识别结果；只有草稿库和回传路径使用脱敏文本。
