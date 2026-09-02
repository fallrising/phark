# SDD-007 驗證紀錄

> 狀態：In Progress
> Branch：`agent/reposts`

## Checkpoints

| Commit | 範圍 |
|--------|------|
| `ddf78de` | Spec、design、風險與 54 項孫任務 |
| This checkpoint | V6 migration、repost relation persistence 與 upgrade evidence |

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
| V6 migration/repository tests | 通過 | 11 tests；0 failures、0 errors、0 skipped |
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

## Persistence checkpoint evidence

- 規格 baseline：draft PR #7 的 CI run `33620782252` 在 commit `ddf78de` 通過
  production container build。
- RED migration：加入最初三個 V6 contracts 後，
  `SchemaMigrationConfigTest` 8 tests 中 3 個如預期失敗；Flyway history 只有 5 rows，
  證明 V6 尚不存在。
- RED repository：focused test compilation 因 planned `RepostState` 與
  `PostRepostRepository` 缺失而失敗。
- Review refactor：把重複的 empty/legacy assertions 合併回既有 upgrade tests，修正一個
  未來會重複插入既有 like 的測試資料，並新增真實 populated V3 → V6 path。
- GREEN focused：
  `mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest,PostRepostRepositoryTest test`
  → 11 tests 通過（7 migration + 4 repository）。
- Regression：`mvn -f backend/pom.xml -B test` → 170 tests 通過。
- Migration coverage 包含 empty、populated V3/V4/V5、pre-Flyway legacy、unknown schema
  fail-closed；驗證 surrogate ID、unique relation、兩個 cascade FK 與兩個 named indexes。
- Repository coverage 包含重送不 bump timestamp、重複 unrepost、two-viewer shared count /
  isolated state，以及實際刪除 account/post 的 cascade behavior。
- Host 無 JDK；以上 Maven commands 在 `maven:3.9-eclipse-temurin-17` container 執行。
