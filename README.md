# Phark Stream Deck

單體 Stream Deck 風格 web 應用：Spring Boot 後端 + React 前端，同源部署於單一 Docker 映像。

Repository：[fallrising/phark](https://github.com/fallrising/phark)

## 快速開始

### Docker build

```bash
docker build -t stream-deck .
```

### Docker run

```bash
docker run --rm \
  -p 8080:8080 \
  -e SESSION_COOKIE_SECURE=false \
  -v stream-deck-data:/data \
  stream-deck
```

開啟 http://localhost:8080

> 使用 **Docker named volume** 掛載 `/data`。若 bind mount 本機目錄，需
> `chown 10001:10001`（容器以 UID 10001 執行）。上例只供本機 HTTP；HTTPS
> production 必須保留 `SESSION_COOKIE_SECURE=true`。

### Health check

```bash
curl -fsS http://localhost:8080/actuator/health
```

## API

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET | `/api/auth/csrf` | 取得 unsafe request 使用的 CSRF header/token |
| POST | `/api/accounts` | 註冊帳號（需 CSRF，不會自動登入） |
| POST | `/api/auth/login` | 建立 authenticated session（需 CSRF） |
| GET | `/api/auth/session` | 取得目前登入帳號；anonymous 時為 `null` |
| POST | `/api/auth/logout` | 清除 session（需登入與 CSRF） |
| GET | `/api/posts` | 最新一頁文章（預設 20 筆） |
| GET | `/api/posts?channel=home&limit=20&before=...` | 依 channel 與 cursor 分頁 |
| POST | `/api/posts` | 以 session identity 建立文章（需登入與 CSRF） |
| PUT | `/api/posts/{postId}/like` | 冪等按讚；回傳權威 count/state（需登入與 CSRF） |
| DELETE | `/api/posts/{postId}/like` | 冪等取消按讚（需登入與 CSRF） |
| PUT | `/api/posts/{postId}/repost` | 冪等轉發；回傳權威 RepostState（需登入與 CSRF） |
| DELETE | `/api/posts/{postId}/repost` | 冪等取消轉發（需登入與 CSRF） |
| GET | `/api/posts/{postId}/replies?limit=20&after=...` | 正序讀取回覆 |
| POST | `/api/posts/{postId}/replies` | 以 session identity 建立單層回覆 |
| GET | `/api/profiles/{handle}` | 公開 profile |
| PATCH | `/api/profiles/me` | 修改自己的 display name/bio |
| GET | `/api/profiles/{handle}/posts` | 該帳號的 cursor-paginated original/repost activities |

`GET /api/posts` 回傳 `{ "items": [...], "nextCursor": "..." }`。將非空的
`nextCursor` 作為下一次 request 的 `before`；`limit` 允許 `1..100`。
回覆 page 使用相同 response envelope，將 `nextCursor` 作為 `after`。
文章 item 包含共享 `likeCount`、`repostCount` 與目前 session 專屬
`likedByViewer`、`repostedByViewer`；anonymous 固定看到 `likedByViewer=false`
與 `repostedByViewer=false`。轉發 activity 包含 nullable 的 `repostedBy`、
`repostedByHandle`、`repostedAt` attribution；original activity 的 attribution
為 null。每個 item 都有 non-null `timelineEntryId` 作為 stable opaque dedup key。

每個 response 都包含 `X-Request-ID`。API 錯誤使用
`application/problem+json`（RFC 9457），以穩定的 `code` 供程式判斷，並在 body
保留相同的 `requestId` 供除錯。完整 schema 與代碼見
[開發指南的 API 錯誤契約](docs/DEVELOPMENT.md#api-錯誤契約)。

建立文章與回覆的 JSON 不接受作者身份；後端只使用 authenticated session account。
Browser client 啟動時先並行取得 CSRF token 與 session，所有 `POST`、`PUT`、
`PATCH`、`DELETE` 都帶伺服器指定的 CSRF header，並在 login/logout 後重新取得 token。
完整 request 範例見
[開發指南的帳號、Session 與 CSRF](docs/DEVELOPMENT.md#帳號session-與-csrf)。

## 文檔

| 文檔 | 說明 |
|------|------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 部署藍圖、技術決策、SQLite 界線 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 專案結構、本地開發、API 規格 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | VPS 完整部署步驟（Traefik + GHCR + CI/CD） |
| [deploy/templates/](deploy/templates/) | VPS 與 GitHub Actions 設定模板 |

## 技術棧

| 層 | 技術 |
|----|------|
| Backend | Java 17, Spring Boot 3.5.16, JdbcClient, SQLite |
| Frontend | React, TypeScript, Vite, shadcn/ui, Tailwind CSS |
| 部署 | Docker Compose, Traefik 3.7.3, GHCR, GitHub Actions |

## 里程碑

- [x] 應用程式與 Docker build
- [x] 三欄時間線 cursor pagination
- [x] 單層回覆與 inline conversation threads
- [x] Session accounts、可信作者歸屬與公開 profiles
- [x] Per-account 冪等 likes 與 optimistic UI
- [x] Per-account 冪等 reposts、original attribution 與 mixed timeline fan-out
- [ ] VPS + Traefik + CI/CD 上線（見 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)）
