# 架構與技術決策

> 最後更新：2026-09-02

本專案目標是在**閒置 VPS** 上建立一條**單機、可重現、可回滾**的部署路徑，**不碰 Kubernetes**。

## 部署藍圖

```text
本機 / Agent Coding CLI
        │
        │ git push master
        ▼
GitHub Actions
  ├─ Docker build + 測試
  ├─ 推送 ghcr.io
  └─ SSH 通知 VPS
        │
        ▼
VPS deploy script
  ├─ 拉取 sha-<commit> 映像
  ├─ Docker Compose 更新服務
  ├─ 等待 healthcheck
  └─ 失敗自動切回舊映像
        │
        ▼
Traefik
  ├─ 監聽 Docker labels
  ├─ 自動設定路由
  └─ Let's Encrypt HTTPS
```

三個核心組件的分工：

| 組件 | 角色 | 類比 K8s |
|------|------|----------|
| **Docker Compose** | 單機服務、網路、volume | Deployment + Service |
| **Traefik** | 依容器 labels 動態路由、HTTPS | Ingress Controller + Ingress |
| **GHCR** | 映像 registry | Container Registry |

參考：[Docker Compose 文檔](https://docs.docker.com/compose/)、[Traefik Docker Provider](https://doc.traefik.io/traefik/providers/docker/)、[GitHub Container Registry](https://docs.github.com/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

## 版本鎖定（2026-07-13）

| 組件 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Node.js | 24 LTS |
| SQLite JDBC | 3.53.2.0 |
| Traefik | 3.7.3 |

## 為何不用自訂 Nginx 事件腳本

自訂 Nginx 腳本的概念類似輕量 Ingress Controller（監聽容器事件 → 產生 upstream → reload），但不建議作為本專案基礎：

- 監聽 `ctr events`（containerd 低階介面），非 Docker Engine 正式 API
- 依賴 `ctr` 輸出格式、PID network namespace、`/proc/net/tcp`，環境敏感
- `PORT=0` 時可能選到錯誤的管理埠或 metrics 埠
- `DOMAIN` 未嚴格驗證
- 無效設定檔殘留會導致後續 `nginx -t` 持續失敗
- 缺少 HTTPS、自動憑證、健康檢查、部署回滾
- 未完整處理 `X-Forwarded-For`、`X-Forwarded-Proto` 等代理 headers

**保留事件驅動自動路由的概念，改用 Traefik：**

```yaml
labels:
  - "traefik.enable=true"
  - "traefik.http.routers.deck.rule=Host(`deck.example.com`)"
  - "traefik.http.services.deck.loadbalancer.server.port=8080"
```

容器啟動後 Traefik 自動更新路由，無需生成 Nginx 設定檔或手動 reload。

## 第一版：單體應用容器

```text
Spring Boot (port 8080)
├─ /api/*          REST API
├─ /actuator/*     健康檢查
├─ SQLite          /data/deck.db（volume 掛載）
└─ React static    編譯後放入 classpath:/static/
```

React build 完成後嵌入 Spring Boot `static` 目錄，production 時前後端同源。

## 帳號、Session 與 content ownership

```text
Browser SPA
  ├─ GET /api/auth/csrf + GET /api/auth/session
  ├─ HttpOnly JSESSIONID + in-memory CSRF token
  └─ POST/PATCH + server-selected CSRF header
              │
              ▼
RequestIdFilter → Spring Security filter chain
                  ├─ CSRF validation
                  ├─ HttpSession SecurityContext
                  └─ RFC 9457 401/403 writer
                              │
                              ▼
Controller → AccountPrincipal.accountId → Service → SQLite ownership FK
```

Password 使用 delegating BCrypt hash；login 失敗固定為 `INVALID_CREDENTIALS`，不提供
account enumeration signal。Login 旋轉 session ID，logout 清除 security context、
session 與 cookie。CSRF token 不放 cookie/localStorage，login/logout 後由 SPA 重新
取得且 mutation 不自動重試。

V4 的 `posts.author_account_id`、`replies.author_account_id` 是 nullable FK。新內容
只從 authenticated principal 寫入 ownership，同時保存 display-name snapshot；
read 以 account 的目前 display name 為優先。V1–V3 legacy rows 不回填、不認領，
仍以原始 `author` snapshot 顯示。

V5 的 `post_likes` 以 `(post_id, account_id)` composite primary key 保證每個帳號對
每篇文章只有一筆 relation，兩個 foreign keys 都使用 `ON DELETE CASCADE`。Like/unlike
使用 conflict-safe insert/精確 delete，在同一 transaction 讀回權威 count 與 viewer
state；identity 只取自 `AccountPrincipal`。Timeline/profile 的 bounded query 同時計算
共享 `likeCount` 與 session 專屬 `likedByViewer`，response 標記
`Cache-Control: private, no-store`。Frontend 先 optimistic toggle，再以 server state
對齊；failure 只復原 like snapshot，不覆蓋同時發生的 reply 或其他 post 更新。

V6 的 `post_reposts` 以 surrogate `id`（AUTOINCREMENT）作為 timeline activity identity，
`(post_id, account_id)` UNIQUE constraint 保證每帳號對每原文只有一筆 repost relation，
兩個 foreign keys 都使用 `ON DELETE CASCADE`。Repost/unrepost 使用
`INSERT ... ON CONFLICT DO NOTHING`（冪等 PUT）與精確 DELETE（冪等 DELETE），在
同一 transaction 讀回權威 `RepostState`（`postId`、`repostCount`、`repostedByViewer`）。
Timeline/profile 的 mixed query 使用 `UNION ALL` 合併 original 與 repost branch，
計算共享 `repostCount` 與 session 專屬 `repostedByViewer`；response 標記
`Cache-Control: private, no-store`。

Mixed timeline JSON 新增 activity identity、shared/viewer state 與 nullable attribution：

```json
{
  "id": 42,
  "timelineEntryId": "repost:17",
  "repostCount": 3,
  "repostedByViewer": false,
  "repostedBy": "Bob",
  "repostedByHandle": "bob_ops",
  "repostedAt": "2026-08-09T12:00:00Z"
}
```

- `id` 永遠是原文 ID；reply/like/repost mutation 都繼續使用這個 ID。
- `timelineEntryId` 是 opaque stable key；original 格式 `post:{postId}`、repost
  格式 `repost:{repostId}`，client 只比較相等與用作 dedup。
- Original activity 的 `repostedBy`、`repostedByHandle`、`repostedAt` 都是 `null`。
- Repost activity 的三個 attribution fields 都 non-null；原文 `author`、`authorHandle`、
  `content`、`channel`、`createdAt` 不被轉發者覆蓋。

Fan-out 規則：每個 original 保留一個 original activity（排序時間為原文 `createdAt`），
每筆 repost relation 產生一個 repost activity（排序時間為 relation `createdAt`）。
Profile posts 包含 owner originals 與 owner repost activities。排序固定為
`activity_at DESC, entry_kind DESC, entry_id DESC`；同秒 tie-break 由 kind
precedence（`POST > REPOST`）與 entry ID 決定。Mixed cursor 是 opaque、無 padding 的
Base64URL v2 token；其 canonical payload 為 `2:<epoch>:<kind>:<entryId>`。Decoder
仍接受 canonical legacy `<epoch>:<postId>` payload 的 Base64URL token，並將它解讀為
original activity boundary。Frontend render key 與 load-more dedup 使用
`timelineEntryId`；互動 patch（like/repost count/state）仍使用原文 `id` 讓所有
visible copies 同步。

V7 新增 `notifications` 與 `notification_read_state` 兩個 table。`notifications`
以 `UNIQUE(reply_id)` 保證同一 reply 最多一筆事件，LIKE/REPOST 的取消後重建仍可
寫入新事件（SQLite 允許多筆 null）；actor/recipient/post/reply 都使用
`ON DELETE CASCADE` foreign keys。`notification_read_state` 以 account 為 primary
key 保存 monotonic high-water `read_through_id`，不 foreign-key 到 notifications，
retention 刪除 boundary row 不會破壞 read state。

Reply/like/repost 的來源 mutation 與 notification insert/prune 在同一個
`@Transactional` service 中（ReplyService 已補上 transaction；like/repost 沿用
原有 transaction boundary），任一寫入失敗即整體 rollback。REPLY 事件只在 reply row
建立後依原文 `author_account_id` 產生；LIKE/REPOST 事件只在 repository insert 真的
建立新 relation 時產生（`ON CONFLICT DO NOTHING` 冪等重送不重複通知）。Self
interaction 與 owner 為 null 的 legacy 文章不產生事件；unlike/unrepost 不撤回歷史
事件，其後的新 PUT 是新的 interaction，會建立新事件與新 ID。

Notification API 只服務 authenticated viewer：actor 是執行 reply/like/repost 的使用者，
身份取自 `AccountPrincipal.accountId`；每筆通知的 recipient 是原文的
`author_account_id`。每個收件者只能看到自己的通知（recipient isolation），
`GET` response 明確設定 `Cache-Control: private, no-store`，security matcher 排在
public `GET /**` permitAll 之前。Paging 使用 notification ID keyset（`id < beforeId`、
`ORDER BY id DESC`、`LIMIT limit + 1`）；notification cursor 是獨立 codec 的
canonical Base64URL `1:<id>` token，與 mixed timeline cursor 不互通。
`readThroughId` 是 monotonic high-water mark：read mutation 使用
`max(current, requested)`，較舊 cursor 不會讓已讀狀態倒退，且 cursor 必須 decode 成
該收件者仍 retained 且 owned 的通知。`unreadCount` 只計算 retained rows 中
`id > readThroughId` 的數量。

Retention：每次成功插入通知後，同一 transaction 刪除該收件者第 501 筆及更舊 rows，
只保留最新 500 筆；`idx_notifications_recipient_page (recipient_account_id, id DESC)`
支援 recipient-scoped pagination/summary。Prune 不重寫 ID 也不降低
`readThroughId`。V7 不回填既有 interactions，部署當下從 0 筆開始，之後每筆新互動
產生事件。

Frontend 新增 `/notifications` client route 與 header unread badge：badge 只在
`unreadCount > 0` 顯示（上限 `99+`），session identity 載入或切換帳號後讀第一頁
取得 badge，logout 立即清空 notification state，避免跨帳號短暫洩漏。「全部標為已讀」
只在 latest cursor non-null 且有未讀時可按，成功後把目前已載入的所有 items 標為
read，並套用 server 回傳的 `readThroughCursor` 與 `unreadCount`；失敗時不做
optimistic clear，保留 badge 並顯示錯誤。本輪不 polling。

HTTP session 是單 instance process memory，idle timeout 預設 30 分鐘；restart 或
重新部署會登出所有使用者。這與 SQLite 的 replicas=1 限制一致，但不是 durable
login。需要水平擴展時，shared session store 與 PostgreSQL 必須一起重新評估。

### 好處

- 只有一個 image、一個 healthcheck
- 前後端同源，無需 CORS
- Traefik 只需一條 route
- SQLite 只被一個應用實例存取
- 部署與回滾簡單

### 後續擴展（本輪不做）

- 前端拆成獨立 Nginx container
- WebSocket
- 背景 worker
- 其他微服務

## 與 Kubernetes 的對照

| Kubernetes 概念 | 目前單機方案 |
|-----------------|-------------|
| Deployment | Docker Compose service |
| Pod | container |
| Service | Docker network DNS |
| Ingress Controller | Traefik |
| Ingress | Traefik labels |
| Container Registry | GHCR |
| Readiness/Liveness probe | Docker healthcheck |
| Rollout | deploy script + SHA image |
| Rollback | `.env` 恢復舊 image |
| PersistentVolume | `/opt/apps/deck/data` |
| ConfigMap | `.env` |
| CronJob | systemd timer（規劃中） |
| Namespace | Compose project + network |
| Scheduler | 暫無（單機） |

本方案**不具備** K8s 的多節點 scheduler、工作負載自動重分配、真正的 rolling deployment 或控制面 HA，但對單台 VPS 已足夠清楚、可控。

## SQLite 現階段界線

SQLite 適合第一版：

- 無需額外 database container
- 備份即單一檔案
- 管理成本低
- 適合單機小型服務

### 限制

- **replicas 必須為 1** — SQLite 採檔案鎖，同一 database 同時只有一個 writer
- WAL 模式可改善 reader/writer 並行，但不等同多節點資料庫
- 不要在 app 運行時直接 `cp deck.db`；應使用 [SQLite Online Backup API](https://sqlite.org/backup.html)

### 何時換 PostgreSQL

- 多 instance 水平擴展
- 大量並行寫入
- 零停機藍綠部署
- 多台 VPS
- 外部 worker 同時寫入

## 安全注意事項

1. **`docker` group 等同 root 權限** — 使用專用 `deploy` 帳號，GitHub Actions 透過 SSH forced command 限制只能執行部署腳本
2. **Traefik 讀取 Docker socket** — 即使 `:ro` 仍為高權限；正式加固時應改用 socket proxy 或 rootless Docker
3. **應用容器不 publish port** — 僅 Traefik 對外開放 80/443；Docker publish 可能繞過 UFW
4. **映像以 `sha-<commit>` 部署** — 不使用漂移的 `latest` tag 作為 production 部署目標
5. **Session cookie 只經 HTTPS** — production profile 預設 Secure、HttpOnly、SameSite=Lax；只有本機 HTTP smoke 可顯式關閉 Secure
6. **CSRF secrets 不持久化** — token 只存在 server session 與 SPA memory，不進 URL、storage 或 log
