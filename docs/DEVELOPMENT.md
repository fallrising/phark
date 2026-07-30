# 開發指南

供本地開發者與接手 LLM 使用的專案說明。

## Repository 結構

```text
phark/
├── backend/                 # Spring Boot 3.5.16 + Java 17
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/example/deck/
│       │   │   ├── DeckApplication.java
│       │   │   ├── config/       # DatabaseConfig, SchemaMigrationConfig, WebConfig
│       │   │   ├── controller/   # PostController, ReplyController
│       │   │   ├── dto/          # CreatePostRequest, CreateReplyRequest
│       │   │   ├── error/        # RFC 9457 codes、exception mapper、violations
│       │   │   ├── model/        # Post, Reply 與 page contracts
│       │   │   ├── repository/   # PostRepository, ReplyRepository (JdbcClient)
│       │   │   ├── service/      # PostService, ReplyService
│       │   │   └── web/          # Request correlation filter
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-prod.properties
│       │       └── db/migration/  # Flyway V1、V2、V3...
│       └── test/
├── frontend/                # React + TypeScript + Vite + shadcn/ui
│   ├── package.json
│   ├── package-lock.json    # 必須提交
│   ├── components.json      # shadcn/ui 設定
│   └── src/
│       ├── api/posts.ts     # 同源 API 呼叫（/api/posts）
│       ├── components/      # Composer, Column, PostCard, ui/*
│       └── types/post.ts
├── Dockerfile               # multi-stage build
├── .dockerignore
├── deploy/templates/        # VPS / CI 設定模板
└── docs/                    # 本目錄
```

## 應用功能：Stream Deck

簡易 TweetDeck 類似版面（**不使用 X/Twitter logo 或商標**）。

| 功能 | 說明 |
|------|------|
| 頁面名稱 | Stream Deck |
| 三欄版面 | Home、Tech、Ops（桌面並排，手機橫向捲動） |
| Post cards | 每欄顯示文章卡片 |
| Composer | 上方輸入 author、content、channel |
| 游標分頁 | 每欄先載入 20 筆，可獨立載入更舊文章 |
| 對話串 | 每篇文章可正序讀取及建立單層回覆 |
| 自動刷新 | 發文後三欄自動重新載入 |

## REST API

### `GET /api/posts`

回傳最新一頁文章。排序固定為 `created_at DESC, id DESC`，使用 keyset cursor，
不使用 `OFFSET`。

```http
GET /api/posts?channel=home&limit=20&before=<opaque-cursor>
```

| 參數 | 預設 | 規則 |
|------|------|------|
| `channel` | 全部 | `home`、`tech`、`ops` |
| `limit` | `20` | `1..100` |
| `before` | — | 上一頁的 `nextCursor`；client 不解析 |

```json
{
  "items": [
    {
      "id": 1,
      "author": "Alice",
      "content": "Hello",
      "channel": "home",
      "createdAt": "2026-07-13T10:00:00Z",
      "replyCount": 2
    }
  ],
  "nextCursor": null
}
```

若仍有更舊資料，`nextCursor` 為 URL-safe Base64 字串；否則為 `null`。無效的
channel、limit 或 cursor 回傳 `400 Bad Request`。

### `POST /api/posts`

建立文章，回傳 `201 Created`。

```json
{
  "author": "Alice",
  "content": "Hello",
  "channel": "home"
}
```

| 欄位 | 規則 |
|------|------|
| `author` | 不可空白，最多 80 字 |
| `content` | 不可空白，最多 500 字 |
| `channel` | 僅允許 `home`、`tech`、`ops`；無效回傳 `400` |

### 建立文章回應範例

```json
{
  "id": 1,
  "author": "Alice",
  "content": "Hello",
  "channel": "home",
  "createdAt": "2026-07-13T10:00:00Z",
  "replyCount": 0
}
```

### `GET /api/posts/{postId}/replies`

回覆依 `created_at ASC, id ASC` 正序排列，使用 `after` cursor 讀取下一頁：

```http
GET /api/posts/1/replies?limit=20&after=<opaque-cursor>
```

回傳 `{ "items": [...], "nextCursor": "..." }`，其中每個 item 包含 `id`、
`postId`、`author`、`content`、`createdAt`。`limit` 允許 `1..100`；不存在的
parent post 回傳 `404`，無效 post id、limit 或 cursor 回傳 `400`。

### `POST /api/posts/{postId}/replies`

建立單層回覆，成功回傳 `201 Created` 與 Reply：

```json
{
  "author": "Bob",
  "content": "Agreed."
}
```

author 與 content 的 validation 規則和文章相同。`replyCount` 由後端計算，
建立成功後再次讀取 timeline 即會增加。

### API 錯誤契約

所有 `/api/**` 非 2xx response 使用 RFC 9457 Problem Details，Content-Type 為
`application/problem+json`：

```json
{
  "type": "urn:phark:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/posts",
  "code": "VALIDATION_FAILED",
  "requestId": "req-example-123",
  "violations": [
    {
      "field": "content",
      "message": "content must not be blank"
    }
  ]
}
```

Client 應以 `code` 判斷流程，不解析 `title`、`detail` 或 validation message。
`detail` 是可顯示文字；`violations` 只在欄位驗證失敗時存在。未知 extension
members 必須忽略。

| Code | HTTP | 說明 |
|------|------|------|
| `VALIDATION_FAILED` | 400 | request body 欄位 constraint 失敗 |
| `INVALID_CHANNEL` | 400 | channel 不在允許清單 |
| `INVALID_LIMIT` | 400 | limit 不是整數或不在 1–100 |
| `INVALID_CURSOR` | 400 | timeline/replies cursor 不合法 |
| `INVALID_POST_ID` | 400 | post ID 不是正整數 |
| `MALFORMED_REQUEST` | 400 | request body 缺失、語法錯誤或無法讀取 |
| `POST_NOT_FOUND` | 404 | 指定文章不存在 |
| `RESOURCE_NOT_FOUND` | 404 | API route/resource 不存在 |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method 不支援 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | request Content-Type 不支援 |
| `INTERNAL_ERROR` | 500 | 未預期錯誤；不回傳內部 exception details |

每個 response（成功或失敗）都有 `X-Request-ID`。Client 可提供符合
`[A-Za-z0-9._-]{1,64}` 的值；缺失或不合法時 server 會產生 UUID。Error body 的
`requestId` 與 response header 相同。支援或除錯時可固定 ID：

```bash
curl -i 'http://127.0.0.1:8080/api/posts?channel=news' \
  -H 'X-Request-ID: local-debug-123'
```

Request ID 只用於關聯 response 與 server log，不是 authentication 或可信資料。
未知 500 對外只回傳固定安全 detail，完整 exception 只記錄於 server log。

### Seed Data

啟動時若資料庫無文章，自動建立至少 9 筆（每個 channel 各 3 筆）。邏輯位於 `PostService.seedData()`。

## 環境變數

| 變數 | 預設 | 說明 |
|------|------|------|
| `APP_DB_PATH` | 本地為 `./data/deck.db`；prod 為 `/data/deck.db` | SQLite 檔案路徑 |
| `SPRING_PROFILES_ACTIVE` | — | 設為 `prod` 啟用 production 設定 |
| `SERVER_PORT` | `8080` | HTTP 埠（prod profile） |

### SQLite 設定

- JDBC URL：`jdbc:sqlite:<APP_DB_PATH>`
- Hikari `maximum-pool-size=1`
- 啟動 PRAGMA：`journal_mode=WAL`、`foreign_keys=ON`、`busy_timeout=5000`
- Schema 由 `db/migration/V*__*.sql` 依版本建立；legacy database 由 guard
  辨識後 baseline。撰寫與 production 操作見 [MIGRATIONS.md](./MIGRATIONS.md)
- **Database 不可打包進 Docker image**，必須透過 volume 掛載

### Spring Production 設定（`application-prod.properties`）

- `server.port=8080`
- `server.forward-headers-strategy=framework`（Traefik 代理必須）
- `server.shutdown=graceful`
- Actuator 僅 expose `health`、`info`
- `/actuator/health` 供 Docker healthcheck 使用

## 本地開發

### 需求

- Java 17 + Maven 3.9+
- Node.js 24 + npm

### 本地前後端

先啟動 backend：

```bash
mvn -f backend/pom.xml spring-boot:run
```

開發環境預設將 SQLite 寫入 `./data/deck.db`。如需其他位置，可設定
`APP_DB_PATH`。

再於另一個 terminal 啟動 frontend：

```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173
npm run lint
npm run build
```

開發伺服器會將 `/api/*` proxy 到 `http://localhost:8080`。

### 僅後端（含測試）

```bash
cd backend
mvn test
```

測試使用 in-memory SQLite（`app.db.path=:memory:`）。

### Docker 本機驗證（推薦）

```bash
docker build --progress=plain -t deck:local .

mkdir -p .local-data
# Linux 若 permission denied：
# sudo chown -R 10001:10001 .local-data

docker run --rm \
  --name deck-local \
  -p 8080:8080 \
  -e APP_DB_PATH=/data/deck.db \
  -e SPRING_PROFILES_ACTIVE=prod \
  -v "$(pwd)/.local-data:/data" \
  deck:local
```

驗證：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/api/posts
```

瀏覽器開啟 http://127.0.0.1:8080

### Volume 權限注意

容器以 **UID/GID 10001**（使用者 `app`）執行。掛載本機目錄時必須可寫：

```bash
sudo chown -R 10001:10001 .local-data
```

使用 Docker named volume 則通常無此問題：

```bash
docker run --rm -p 8080:8080 -v stream-deck-data:/data stream-deck
```

## 測試

| 範圍 | 命令 | 覆蓋 |
|------|------|------|
| Backend | `mvn -f backend/pom.xml test` | posts/replies、migration、cursor、Problem Details、correlation、validation |
| Frontend lint | `npm run lint`（在 `frontend/`） | oxlint |
| Frontend build | `npm run build`（在 `frontend/`） | TypeScript + Vite |
| 整合 | `docker build -t stream-deck .` | 含 frontend lint/build + Maven test |

## 專案約定

1. **不使用 JPA / Hibernate** — 資料存取使用 `JdbcClient`
2. **Package 名稱** — `com.example.deck`
3. **前後端同源** — production 不設定獨立 API domain；前端 `fetch('/api/...')`
4. **SPA 路由** — `WebConfig` 將非 API/actuator 請求 fallback 到 `index.html`
5. **單一 replica** — SQLite 限制，不可水平擴展多寫入實例
6. **不建立 Kubernetes 設定** — 部署走 Docker Compose + Traefik

## 給接手 LLM 的提示

- 改 API 時同步更新 `PostControllerTest` 與 `frontend/src/api/posts.ts`
- 新增 channel 需改：`CreatePostRequest`、新增一個 forward-only migration 更新
  CHECK constraint、`PostService` seed、`frontend` 的 `Channel` type 與 UI；不可修改
  已發布的 migration
- 部署相關設定在 `deploy/templates/` 與 `docs/DEPLOYMENT.md`，不在應用程式碼內
- CI/CD workflow 模板在 `deploy/templates/github/workflows/ci-cd.yml`，加入 repo 前需設定 GitHub Secrets
