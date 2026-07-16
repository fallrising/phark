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
│       │   │   ├── config/       # DatabaseConfig, WebConfig (SPA fallback)
│       │   │   ├── controller/   # PostController
│       │   │   ├── dto/          # CreatePostRequest
│       │   │   ├── model/        # Post
│       │   │   ├── repository/   # PostRepository (JdbcClient)
│       │   │   └── service/      # PostService (含 seed data)
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-prod.properties
│       │       └── schema.sql
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
| 自動刷新 | 發文後三欄自動重新載入 |

## REST API

### `GET /api/posts`

回傳所有文章（依 `created_at` 降序）。

### `GET /api/posts?channel=home`

依 channel 過濾。允許值：`home`、`tech`、`ops`。

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

### 回應範例

```json
{
  "id": 1,
  "author": "Alice",
  "content": "Hello",
  "channel": "home",
  "createdAt": "2026-07-13T10:00:00Z"
}
```

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
- Schema 由 `schema.sql` 建立
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
| Backend | `mvn -f backend/pom.xml test` | invalid channel → 400、blank content → 400、seed data、CRUD |
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
- 新增 channel 需改：`CreatePostRequest`、`schema.sql` CHECK、`PostService` seed、`frontend` 的 `Channel` type 與 UI
- 部署相關設定在 `deploy/templates/` 與 `docs/DEPLOYMENT.md`，不在應用程式碼內
- CI/CD workflow 模板在 `deploy/templates/github/workflows/ci-cd.yml`，加入 repo 前需設定 GitHub Secrets
