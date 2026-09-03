# SDD-009 驗證紀錄

> 狀態：Draft（scaffold；本輪只建立 skeleton，沒有 gate 被標記為通過）
> Branch：`agent/search`（已存在，本 worker 的 `worker/sdd009-t002` 以其為基底）
> Base merge：`08adeab`（SDD-008 merged，master/origin/master 一致）

## Checkpoints

| Commit | 範圍 | 狀態 |
|--------|------|------|
| This checkpoint | Spec、design、風險與 54 項孫任務（Stage A） | 通過文件檢查 |
| （待填充） | V8 migration、rebuild backfill 與 trigger 同步/rollback | 未執行 |
| （待填充） | Query compilation 與 dedicated `s1:` search cursor codec | 未執行 |
| （待填充） | Search repository/service、HTTP API 與 security/cache contract | 未執行 |
| （待填充） | Typed search client、route、SearchView 與 stale/session refresh | 未執行 |
| （待填充） | Search API、architecture、migration runbook 與 roadmap 文件 | 未執行 |
| （待填充） | Production image、V7→V8 migration 與 full runtime delivery evidence | 未執行 |
| （待填充） | Final SDD status、54/54 孫任務、final-head 與 post-merge master CI | 未執行 |

本 checkpoint 尚未產生 commit SHA；上表 SHA 與狀態在對應 stage 完成後才填寫，未執行的 gate
一律標記「未執行」，不沿用任何上一輪的通過宣稱。

Stage A 經主代理完整審查、一次 REWORK 與最終修訂；`git diff --check` 通過，任務樹精確為
6 stages、18 tasks、54 subtasks，其中 A 的 9 個 subtasks 已完成。

## Inherited baseline

- Base merge：`08adeabc96ae7e29a8800fe79731cd42bf846884`（SHORT `08adeab`）。
- SDD-008 已 merge；其驗證證據見 `docs/specs/008-notifications/verification.md`。
- T-001 read-only inventory：sqlite-jdbc 3.53.2.0 bundled SQLite 含 `fts5`（binary inspection，
  尚未 live 複驗）。
- 本輪 base branch 為已存在的 `agent/search`；worker branch `worker/sdd009-t002` 在其上作業。

## Required gates（skeleton，全部未執行）

| Gate | 命令/範圍 | 狀態 | 證據 |
|------|-----------|------|------|
| V8 migration/trigger tests | `mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest test`（V8 案例加入既有 migration test 類，含 rebuild 等價、trigger 同步/rollback、`PRAGMA integrity_check`） | 未執行 | N tests；0 failures/errors/skipped（待填充） |
| Query compile/cursor tests | `mvn -f backend/pom.xml -B -Dtest=SearchQueryCompilerTest,SearchCursorCodecTest test`（新增類；SearchQueryCompiler 與 dedicated `s1:` codec 各一） | 未執行 | N tests（待填充） |
| Repository/API/security tests | `mvn -f backend/pom.xml -B -Dtest=SearchRepositoryTest,SearchControllerTest,AuthSecurityContractTest,ApiErrorContractTest test`（service 行為由 repository/controller contract 涵蓋） | 未執行 | N tests（待填充） |
| Complete backend regression | `mvn -f backend/pom.xml -B test` | 未執行 | N tests；含既有 257 baseline 不回歸（待填充） |
| Frontend lint/build | `npm run lint`；`npm run build`（`tsc -b && vite build`） | 未執行 | oxlint 0 diagnostics、modules count（待填充） |
| Multi-stage Docker build | `docker build --progress=plain -t phark:sdd009 .` | 未執行 | image digest `sha256:...`、runtime user（待填充） |
| Production-like runtime smoke | `/tmp/phark-sdd009-runtime.sh`（script 待建立） | 未執行 | 見下方 runtime scenarios（待填充） |
| GitHub Actions final head | workflow run URL on `agent/search` head | 未執行 | run/job URL（待填充） |
| GitHub Actions post-merge `master` | workflow run URL on merged head | 未執行 | run/job URL（待填充） |

完成時記錄 exact commands、RED failures、test counts、image digest、runtime scenarios、workflow
run/job URL 與 commit SHA；未實際執行的 gate 不標記為通過。

## Planned exact gates

```text
mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest test
mvn -f backend/pom.xml -B -Dtest=SearchQueryCompilerTest,SearchCursorCodecTest test
mvn -f backend/pom.xml -B -Dtest=SearchRepositoryTest,SearchControllerTest,AuthSecurityContractTest,ApiErrorContractTest test
mvn -f backend/pom.xml -B test
npm run lint
npm run build
docker build --progress=plain -t phark:sdd009 .
bash -n /tmp/phark-sdd009-runtime.sh && /tmp/phark-sdd009-runtime.sh
```

Host 若仍無 JDK，使用 repository Dockerfile 或 pinned Maven container；Node 沿用 repository
已使用的 nvm/npm。不得以 mock 取代 SQLite migration/transaction 與 production wiring evidence。

## Required runtime evidence（placeholder 清單）

- Image：`phark:sdd009` digest `sha256:...`；`SPRING_PROFILES_ACTIVE=prod`、`APP_DB_PATH=/data/deck.db`、
  non-root `10001:10001`。
- Clean migration：empty schema 套用到 V8；health `UP`；`PRAGMA integrity_check=ok`；
  `SELECT sqlite_version()` 與 `PRAGMA compile_options` FTS5 確認為 production 原生 SQLite，而非
  mock。
- Populated V7→V8：以既有 V7 database（或 `phark:...` image 產生）原地啟動，只套用 V8；既有
  accounts/posts/IDs 保留；rebuild 後每篇既有 original post 都能以內容配對；search index 與
  posts counts 一致。`sqlite_master` 含 FTS virtual table 與三個 trigger。
- Trigger 同步：新增/更新/刪除 post 後原文可/不可搜尋一致；trigger failure 下 post 寫入 rollback。
- Query/cursor：Unicode term、non-whitespace control rejection、operator/wildcard 樣式 input 不改變 query 結構、malformed syntax
  到不了 MATCH、`foo*` 非 prefix matching、punctuation-only term 回 `400 INVALID_QUERY`；equal
  `created_at` tiebreak、bounded limit、invalid/missing `q`、invalid limit/cursor → RFC 9457、
  dedicated `s1:` cursor 拒絕 legacy timeline/timeline v2/notification token、newest-first 分頁
  不漏不重；unexpected FTS/operational failure 回 `INTERNAL_ERROR` 並 log。
- Viewer-aware：anonymous 結果 `likedByViewer`/`repostedByViewer` 為 `false`（非 null）；
  authenticated 為對應 boolean；response `Cache-Control: private, no-store`；account/session 改變
  以新 viewer 重跑目前 query、logout 以 anonymous 重跑有效 public route。
- Frontend/SPA：`/search?q=...` direct load、navigation/back、loading/empty/error、load-more
  dedupe、stale query response 棄置、session change refresh、authenticated reply/like/repost 互動
  不劣化。
- CI：final-head（Draft PR head）與 post-merge `master` 各自的 GitHub Actions run/job URL。

## Decision evidence（待實作時記錄）

- T-001 建議的 cold/no-backfill 與 authenticated search matcher 已被 orchestrator 拒絕；V8 全量
  rebuild backfill，public GET 維持在 `GET /**` 之下。
- matching 用 FTS、ordering 用 `(posts.created_at DESC, posts.id DESC)`；cursor 只當 ordering
  boundary，不暴露 rank。
- Search cursor payload 為 dedicated `s1:<epoch-second>:<positive-id>`，與 legacy timeline
  `1:<epoch>:<id>`、timeline v2 `2:<epoch>:<kind>:<id>` 及 notification `1:<id>` byte-distinct。
- `Post` JSON 的 `likedByViewer`/`repostedByViewer` 是 boolean（anonymous 為 false）；search 只回
  original rows，`timelineEntryId = post:<id>`、repost attribution null。Search endpoint 直接沿用既有
  `Post` shape。
- Raw FTS syntax 不是 API；`SearchQueryCompiler` 輸出 bound 參數、逐 term quoted phrase AND，無
  wildcard/prefix；quoting neutralizes operator syntax，unicode61 punctuation 是 separator 而非
  可搜尋資料。
- Backend 與 frontend 都復用既有 `PostPage` shape，不建立同形 SearchPage/type。
- `INVALID_QUERY` 新增至 `ApiErrorCode`（required）；missing `q` 以 nullable request 參數進入
  service validation。unexpected FTS/operational failure 回 `INTERNAL_ERROR` 並 log，不廣義映射
  成 `INVALID_QUERY`。
- 加入每一 stage 的實作決策與驗證失敗/修正證據。

## Open unknowns（隨實作逐一關閉）

- FTS5 需 production runtime `PRAGMA compile_options` smoke 複驗（T-001 目前只有 binary
  inspection 證據）。
- rebuild 在既有 posts 數量下的成本未量測。
- `created_at` 格式與 epoch-second round-trip 精確性需 live migration/runtime 驗證。
- Keyset index 是否可直接沿用 timeline index 需由 migration tests 確認。
