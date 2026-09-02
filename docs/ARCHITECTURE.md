# 架構與技術決策

> 最後更新：2026-07-30

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
