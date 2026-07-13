# Stream Deck

單體 Stream Deck 風格 web 應用：Spring Boot 後端 + React 前端，同源部署於單一 Docker 映像。

Repository：[fallrising/phark](https://github.com/fallrising/phark)

## 快速開始

### Docker build

```bash
docker build -t stream-deck .
```

### Docker run

```bash
docker run --rm -p 8080:8080 -v stream-deck-data:/data stream-deck
```

開啟 http://localhost:8080

> 使用 **Docker named volume** 掛載 `/data`。若 bind mount 本機目錄，需 `chown 10001:10001`（容器以 UID 10001 執行）。

### Health check

```bash
curl -fsS http://localhost:8080/actuator/health
```

## API

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET | `/api/posts` | 所有文章 |
| GET | `/api/posts?channel=home` | 依 channel 過濾 |
| POST | `/api/posts` | 建立文章 |

```bash
curl -fsS -X POST http://localhost:8080/api/posts \
  -H 'Content-Type: application/json' \
  -d '{"author":"Alice","content":"Hello","channel":"home"}'
```

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
- [ ] VPS + Traefik + CI/CD 上線（見 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)）