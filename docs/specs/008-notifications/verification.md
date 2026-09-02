# SDD-008 驗證紀錄

> 狀態：In Progress
> Branch：`agent/notifications`

## Checkpoints

| Commit | 範圍 |
|--------|------|
| `f805560` | Spec、design、風險與 54 項孫任務 |
| This checkpoint | V7 migration、notification persistence/page projection 與 500-row retention |

## Inherited baseline

- Base merge：`62f470c319d772649f27a330f2f4fa73b96f894b`（SDD-007 merged）。
- SDD-007 final documentation head `4a63b03` 的 GitHub Actions run `33655201659` 通過。
- SDD-007 delivery gate：201 backend tests、frontend lint/TypeScript/Vite、V1–V6 migrations、
  Docker build 與 clean/populated runtime smoke 均通過。

## Required gates

| Gate | 狀態 | 證據 |
|------|------|------|
| V7 migration/repository tests | 通過 | 11 tests；0 failures、0 errors、0 skipped |
| Transactional event emission tests | 待執行 | C 階段 |
| Notification cursor/read API/security tests | 待執行 | D 階段 |
| Complete backend regression | 通過（B checkpoint） | 205 tests；0 failures、0 errors、0 skipped |
| Frontend lint/build | 待執行 | E 階段 |
| Multi-stage Docker build | 待執行 | F 階段 |
| Production-like runtime smoke | 待執行 | F 階段 |
| GitHub Actions delivery head | 待執行 | F 階段 |

完成時記錄 exact commands、RED failures、test counts、image digest、runtime scenarios、workflow
run/job URL 與 commit SHA；未實際執行的 gate 不標記為通過。

## Decision evidence

- 主代理盤點確認：owned reply/like/repost actor 都來自 authenticated principal；post owner 是
  nullable internal account ID，legacy owner null。
- Like/repost repository 已使用 `ON CONFLICT DO NOTHING`，但目前回 `void`；最小改動是回
  affected-row boolean，讓 transaction 只在真正 insert 時產生事件。
- Reply service 尚無 transaction，like/repost service 已有；REPLY 需要補 transactional boundary。
- 現有 public `GET /**` security matcher 先匹配所有 GET；notification GET 的 authenticated
  matcher 必須排在它之前。
- 選擇保存事件而非讀取時動態 UNION：取消 interaction 不應刪歷史通知，且 unread high-water
  需要 immutable event ID。
- 選擇 recipient-scoped synchronous 500-row prune：現階段不引入 scheduler/queue，並讓讀取、
  分頁與未讀成本有 hard bound。
- OpenCode 的 DeepSeek V4 Flash model ID 已確認為 `opencode-go/deepseek-v4-flash`；初次實測
  被中國託管 opt-in gate 擋下。使用者完成 workspace opt-in 後，以精確 `run -m` 形式成功
  執行 B.2 RED 與 B.3 GREEN 任務。
- OpenCode 唯讀 inventory 確認 V7、三個 service mutation points、獨立 cursor codec、header /
  App route 與 migration/contract test locations；其四個建議經主代理審查後未採用：通知不按
  unread-first 排序（會破壞穩定 ID keyset）、read endpoint 使用 monotonic PUT 而非 PATCH、
  actor/content 讀 current joined data 而不新增 snapshot duplication，且 LIKE/REPOST 不加永久
  `(post, actor, type)` unique（否則取消後重做無法產生新事件）。冪等性由 active relation 的
  affected-row signal 保證。

## Planned exact gates

```text
mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest,NotificationRepositoryTest test
mvn -f backend/pom.xml -B -Dtest=NotificationEventContractTest,PostLikeMutationContractTest,PostRepostMutationContractTest,ReplyControllerTest test
mvn -f backend/pom.xml -B -Dtest=NotificationCursorCodecTest,NotificationControllerTest,AuthSecurityContractTest test
mvn -f backend/pom.xml -B test
npm run lint
npm run build
docker build -t phark:sdd008 .
```

Host 若仍無 JDK，使用 repository Dockerfile 或 pinned Maven container；Node 沿用 repository
已使用的 nvm/npm。不得以 mock 取代 SQLite migration/transaction 與 production wiring evidence。

## Persistence checkpoint evidence

- Migration RED 經主代理把 OpenCode 初稿的重複 scenarios 合併回既有 upgrade contracts；
  `SchemaMigrationConfigTest` 8 tests 中 7 個如預期只因 history 停在 V6 而失敗，unknown schema
  fail-closed test 保持通過。
- Repository RED：新增 `NotificationRepositoryTest` 後 test compilation 產生 39 個預期
  `cannot find symbol`，只指向尚不存在的 `NotificationRepository`、`NotificationType` 與
  `NotificationItem`。
- GREEN migration 新增 immutable V7 tables、三 type/reply-shape checks、reply uniqueness、五個
  cascade FKs 與 recipient/ID index；empty、V3/V4/V5/V6、兩種 pre-Flyway legacy paths 都到
  V7，既有資料與 IDs 保留且不回填通知。
- Repository `insertAndPrune` 回 generated event ID，保留每個 recipient 最新 500 rows；
  `findPage` 以單一 parameterized JOIN 回 current actor/post/reply content、read flag 與 strict
  ID-desc/before page。另一 recipient 不受 prune 影響。
- 初次 GREEN 暴露 sqlite-jdbc nullable `reply_id` mapping 問題；改用 `getLong` 並立即保存
  `wasNull()`，避免後續 getter 覆寫 JDBC null state。
- Focused：
  `mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest,NotificationRepositoryTest test`
  → 11 tests 通過（8 migration + 3 repository）。
- Regression：`mvn -f backend/pom.xml -B test` → 205 tests 通過。
- Host 無 JDK；以上 commands 在 `maven:3.9-eclipse-temurin-17` container 執行。
- B checkpoint 尚未接 event services、read cursor/API 或 frontend；這些分別由 C、D、E 階段
  驗證，不把 repository projection 誤報成可用 HTTP behavior。
