# SDD-007 驗證紀錄

> 狀態：In Progress
> Branch：`agent/reposts`

## Checkpoints

| Commit | 範圍 |
|--------|------|
| This checkpoint | Spec、design、風險與 54 項孫任務 |

## Inherited baseline

- Base merge：`5ae6c01f085e9fd04db3a7c6ffb0a5122a6c18bb`（SDD-006 merged）。
- 同一 code state 的 final PR head `b045484` 已由 GitHub Actions CI run
  `33619421434` 通過 production container build。
- SDD-006 delivery gate：164 backend tests、frontend lint/TypeScript/Vite、V1–V5
  migrations 與 Docker runtime smoke 均通過。
- SDD-007 尚未改 production code；RED 前會再執行 repository-native baseline/focused gate。

## Required gates

| Gate | 狀態 | 證據 |
|------|------|------|
| V6 migration/repository tests | Pending | — |
| Mixed cursor/timeline/profile tests | Pending | — |
| Repost API/security tests | Pending | — |
| Complete backend regression | Pending | — |
| Frontend lint | Pending | — |
| Frontend production build | Pending | — |
| Multi-stage Docker build | Pending | — |
| Production-like runtime smoke | Pending | — |
| GitHub Actions final head | Pending | — |

完成時記錄 exact commands、RED failures、test counts、image digest、runtime scenarios、
workflow run/job URL 與 commit SHA；未實際執行的 gate 不標記為通過。

## Decision evidence

- 主代理盤點確認：現有 timeline/profile 都只讀 `posts`，cursor 是
  `<epoch>:<positivePostId>`；frontend render/load-more 以 post ID 去重。
- OpenCode read-only inventory 確認 V6、repository/service/controller、SecurityConfig 與
  frontend optimistic helper 是主要 extension points。
- OpenCode 建議先做 count-only relation、排除 attribution/fan-out；此建議與 ROADMAP 的
  SDD-007 明確子項衝突，因此不採用。設計改為獨立 relation/event ID + mixed UNION，
  保留原文 ID 並滿足 attribution/fan-out。
- 拒絕把 repost 寫成一般 `posts` row：那會讓公開 activity ID 可被 reply/like API 當作
  原文，造成 interaction ownership 分叉。

## Planned exact gates

```text
mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest,PostRepostRepositoryTest test
mvn -f backend/pom.xml -B -Dtest=PostCursorCodecTest,PostRepostReadContractTest,PostControllerTest,ProfileContractTest test
mvn -f backend/pom.xml -B -Dtest=PostRepostMutationContractTest,AuthSecurityContractTest test
mvn -f backend/pom.xml -B test
npm run lint
npm run build
docker build -t phark:sdd007 .
```

Host 若仍無 JDK/Node，使用 repository Dockerfile 或 pinned Maven/Node container 執行，
不得以 mock 取代 mixed SQLite query 與 production wiring evidence。
