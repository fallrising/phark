# SDD-008 驗證紀錄

> 狀態：In Progress
> Branch：`agent/notifications`

## Checkpoints

| Commit | 範圍 |
|--------|------|
| This checkpoint | Spec、design、風險與 54 項孫任務 |

## Inherited baseline

- Base merge：`62f470c319d772649f27a330f2f4fa73b96f894b`（SDD-007 merged）。
- SDD-007 final documentation head `4a63b03` 的 GitHub Actions run `33655201659` 通過。
- SDD-007 delivery gate：201 backend tests、frontend lint/TypeScript/Vite、V1–V6 migrations、
  Docker build 與 clean/populated runtime smoke 均通過。

## Required gates

| Gate | 狀態 | 證據 |
|------|------|------|
| V7 migration/repository tests | 待執行 | B 階段 |
| Transactional event emission tests | 待執行 | C 階段 |
| Notification cursor/read API/security tests | 待執行 | D 階段 |
| Complete backend regression | 待執行 | B–F 各 checkpoint |
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
- OpenCode 的 DeepSeek V4 Flash model ID 已確認為 `opencode-go/deepseek-v4-flash`；實測模型
  正確啟動前被 provider 擋下，因中國託管版本需要 workspace owner 明確 opt-in。本輪未代替
  使用者接受資料託管條款。
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
