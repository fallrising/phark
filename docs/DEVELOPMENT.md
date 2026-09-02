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
│       │   │   ├── config/       # Database, migration, security 與 SPA config
│       │   │   ├── controller/   # Auth、account、profile、post、reply、like APIs
│       │   │   ├── dto/          # JSON request/response boundaries
│       │   │   ├── error/        # RFC 9457 codes、exception mapper、violations
│       │   │   ├── model/        # Account、profile、content 與 page contracts
│       │   │   ├── repository/   # Account/Post/Reply/PostLike JdbcClient repositories
│       │   │   ├── security/     # Principal、UserDetails 與 Problem writers
│       │   │   ├── service/      # Account、Post、Reply、PostLike services
│       │   │   └── web/          # Request correlation filter
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-prod.properties
│       │       └── db/migration/  # Immutable Flyway V1...V5
│       └── test/
├── frontend/                # React + TypeScript + Vite + shadcn/ui
│   ├── package.json
│   ├── package-lock.json    # 必須提交
│   ├── components.json      # shadcn/ui 設定
│   └── src/
│       ├── api/client.ts    # Problem Details、same-origin fetch、CSRF memory
│       ├── api/accounts.ts  # Account/session/profile typed calls
│       ├── api/posts.ts     # Post/reply typed calls
│       ├── components/      # Auth、profile、timeline、composer、ui/*
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
| Composer | 登入後以 session identity 輸入 content、channel |
| 游標分頁 | 每欄先載入 20 筆，可獨立載入更舊文章 |
| 對話串 | 每篇文章可正序讀取及建立單層回覆 |
| 帳號 | Register、login、logout 與 30 分鐘 server-side session |
| Profile | 公開 profile、作者文章分頁與 owner display name/bio 編輯 |
| Likes | 每帳號冪等 like/unlike、權威 count 與 optimistic rollback |
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
      "authorHandle": "alice_ops",
      "content": "Hello",
      "channel": "home",
      "createdAt": "2026-07-13T10:00:00Z",
      "replyCount": 2,
      "likeCount": 3,
      "likedByViewer": true
    }
  ],
  "nextCursor": null
}
```

若仍有更舊資料，`nextCursor` 為 URL-safe Base64 字串；否則為 `null`。無效的
channel、limit 或 cursor 回傳 `400 Bad Request`。

### `POST /api/posts`

需 authenticated session 與有效 CSRF；作者只取自 session account。建立成功回傳
`201 Created`。

```json
{
  "content": "Hello",
  "channel": "home"
}
```

| 欄位 | 規則 |
|------|------|
| `content` | 不可空白，最多 500 字 |
| `channel` | 僅允許 `home`、`tech`、`ops`；無效回傳 `400` |

### 建立文章回應範例

```json
{
  "id": 1,
  "author": "Alice",
  "authorHandle": "alice_ops",
  "content": "Hello",
  "channel": "home",
  "createdAt": "2026-07-13T10:00:00Z",
  "replyCount": 0,
  "likeCount": 0,
  "likedByViewer": false
}
```

### `GET /api/posts/{postId}/replies`

回覆依 `created_at ASC, id ASC` 正序排列，使用 `after` cursor 讀取下一頁：

```http
GET /api/posts/1/replies?limit=20&after=<opaque-cursor>
```

回傳 `{ "items": [...], "nextCursor": "..." }`，其中每個 item 包含 `id`、
`postId`、`author`、nullable `authorHandle`、`content`、`createdAt`。`limit` 允許
`1..100`；不存在的 parent post 回傳 `404`，無效 post id、limit 或 cursor 回傳
`400`。

### `POST /api/posts/{postId}/replies`

需 authenticated session 與有效 CSRF；建立成功回傳 `201 Created` 與 Reply：

```json
{
  "content": "Agreed."
}
```

作者由 session 決定，content validation 規則和文章相同。`replyCount` 由後端
計算，建立成功後再次讀取 timeline 即會增加。Account-owned content 的 `author`
使用目前 display name，`authorHandle` 是 canonical handle；V1–V3 legacy content
保留既有 author snapshot 且 `authorHandle=null`。

### `PUT /api/posts/{postId}/like` / `DELETE /api/posts/{postId}/like`

兩者都需 authenticated session 與有效 CSRF，而且都可安全重送。PUT 在 relation
已存在時為 no-op；DELETE 在 relation 不存在時為 no-op。成功一律回 `200`：

```json
{
  "postId": 1,
  "likeCount": 3,
  "likedByViewer": true
}
```

Actor 只取自 session，不接受 request body 中的 account identity。`postId <= 0` 回
`INVALID_POST_ID`，不存在的正 ID 回 `POST_NOT_FOUND`。Self-like 與 legacy post
like 都允許；mutation 不改文章 timestamp 或 timeline cursor order。

Timeline 與 profile-post GET 的 `likeCount` 對所有 viewer 相同；
`likedByViewer` 依目前 session 計算，anonymous 固定為 false。這兩種 response 使用
`Cache-Control: private, no-store`，不可放進共享 cache。

### 帳號、Session 與 CSRF

| Method | Path | Auth | 說明 |
|--------|------|------|------|
| GET | `/api/auth/csrf` | Public | 回傳 `headerName` 與 opaque token；`no-store` |
| POST | `/api/accounts` | Public + CSRF | 註冊；成功 201，不自動登入 |
| POST | `/api/auth/login` | Public + CSRF | JSON credentials；成功旋轉 session ID |
| GET | `/api/auth/session` | Public | 回傳 `{ "account": profile-or-null }` |
| POST | `/api/auth/logout` | Session + CSRF | 清除 context、session 與 cookie；成功 204 |
| GET | `/api/profiles/{handle}` | Public | 公開 profile |
| PATCH | `/api/profiles/me` | Session + CSRF | 修改自己的 display name 與 bio |
| GET | `/api/profiles/{handle}/posts` | Public | 作者文章 keyset page |

Handle canonicalize 為 lowercase，必須是 3–15 個 ASCII `a-z`、`0-9`、`_`。
Display name 為 1–50 characters，bio 最多 160 characters，password 為 12–72
UTF-8 bytes。Password 只保存 delegating BCrypt hash，永不出現在 response 或 log。

同源 client 的必要順序：

1. 啟動時呼叫 `GET /api/auth/csrf`，token 只保存在記憶體。
2. 同時呼叫 `GET /api/auth/session`，建立目前 identity state。
3. 所有 `POST`、`PUT`、`PATCH`、`DELETE` 都使用 response 指定的 header name/token。
4. Login/logout 完成後丟棄舊 token，等待新的 `/api/auth/csrf` response 才允許
   下一個 mutation。
5. `CSRF_TOKEN_INVALID` 不自動重試 mutation；顯示安全錯誤並重新載入 token/page。

以下 shell sequence 示範 registration；`csrf.json` 與 cookie jar 都是敏感的本機
暫存檔，不可提交或記錄 token 值：

```bash
BASE_URL=http://127.0.0.1:8080
curl -fsS -c cookies.txt -o csrf.json "$BASE_URL/api/auth/csrf"

# 從 csrf.json 讀取 headerName/token，並在同一 cookie jar 的 POST 中帶入：
curl -fsS -b cookies.txt -c cookies.txt \
  -X POST "$BASE_URL/api/accounts" \
  -H 'Content-Type: application/json' \
  -H '<headerName>: <token>' \
  -d '{"handle":"alice_ops","displayName":"Alice","password":"correct horse battery staple"}'
```

登入 request 使用 `{ "handle": "alice_ops", "password": "..." }`。Profile update
request 使用 `{ "displayName": "Alice Ops", "bio": "..." }`。Frontend
`api/client.ts` 已實作相同的 fail-closed sequence，且不使用 local/session storage。

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
| `INVALID_CREDENTIALS` | 401 | 登入失敗；不區分 handle 或 password 原因 |
| `AUTHENTICATION_REQUIRED` | 401 | protected endpoint 缺少有效 session |
| `CSRF_TOKEN_INVALID` | 403 | unsafe request 缺少或使用無效 token |
| `ACCESS_DENIED` | 403 | authenticated account 沒有權限 |
| `PROFILE_NOT_FOUND` | 404 | 指定 handle 不存在 |
| `POST_NOT_FOUND` | 404 | 指定文章不存在 |
| `RESOURCE_NOT_FOUND` | 404 | API route/resource 不存在 |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method 不支援 |
| `HANDLE_UNAVAILABLE` | 409 | canonical handle 已被註冊 |
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
| `SESSION_COOKIE_SECURE` | 本地 `false`；prod `true` | HTTPS production 必須為 `true` |
| `SERVER_SERVLET_SESSION_TIMEOUT` | `30m` | In-memory HTTP session idle timeout |

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
- Session cookie 為 HttpOnly、SameSite=Lax、Secure；timeout 預設 30 分鐘
- Actuator 僅 expose `health`、`info`
- `/actuator/health` 供 Docker healthcheck 使用

Session 只存在單一 application instance 的記憶體；container restart、重新部署或
process crash 都會登出所有使用者。這是目前 replicas=1 邊界，不可把它解讀為
persistent login。多 instance 前必須先引入共享 session store。

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
  -e SESSION_COOKIE_SECURE=false \
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
| Backend | `mvn -f backend/pom.xml test` | account/auth/CSRF/ownership/profile、content/likes、migration、errors |
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

- 改 post API 時同步更新 controller contract tests 與 `frontend/src/api/posts.ts`
- 新增 channel 需改：`CreatePostRequest`、新增一個 forward-only migration 更新
  CHECK constraint、`PostService` seed、`frontend` 的 `Channel` type 與 UI；不可修改
  已發布的 migration
- 部署相關設定在 `deploy/templates/` 與 `docs/DEPLOYMENT.md`，不在應用程式碼內
- CI/CD workflow 模板在 `deploy/templates/github/workflows/ci-cd.yml`，加入 repo 前需設定 GitHub Secrets
