# SDD-010 驗證紀錄（scaffold）

> 狀態：In Progress — scaffold checkpoint（僅文件，未宣稱任何實作 gate）
> Branch：`agent/media-attachments`
> Baseline：`e2c8e11330628baab457269ab21425fe22b5dc16`（本次 scaffold 起點 HEAD）
> REWORK 1：spec/design/tasks/verification 依 orchestrator review 修正後已併回；以下 gates
> 仍全部 pending，此檔案不陳述任何未執行檢查為通過。

## Checkpoints

| Commit | 範圍 | 狀態 |
|--------|------|------|
| `e2c8e11` | baseline HEAD；本 scaffold 記錄起點 | 記錄 baseline |
| (scaffold) | 4 份 spec/design/tasks/verification（docs only；REWORK 1 修正後） | 通過文件檢查 |
| TBD | V9 migration + metadata repository（Stage B） | pending |
| TBD | ImageValidator + LocalMediaStorage（Stage C） | pending |
| TBD | multipart create + media read API（Stage D） | pending |
| TBD | Frontend compose/preview/rendering（Stage E） | pending |
| TBD | 文件、runtime 與 CI 交付（Stage F） | pending |

## Required gates

| Gate | 狀態 | 證據 |
|------|------|------|
| V9 migration/metadata tests（含 sha256 CHECK） | pending | 尚未執行 |
| ImageValidator/LocalMediaStorage tests（byte-oriented boundary） | pending | 尚未執行 |
| Service/controller/security/error tests（含 `handleMissingServletRequestPart`、type-mismatch、413 mapping） | pending | 尚未執行 |
| Serving 前 byte length/SHA-256 驗證 tests | pending | 尚未執行 |
| Complete backend regression | pending | 尚未執行 |
| Frontend lint/build | pending | 尚未執行 |
| Multi-stage Docker build | pending | 尚未執行 |
| Clean V1–V9 與 populated V8→V9 runtime | pending | 尚未執行 |
| Upload/read/restart/cache/error/rollback/path runtime | pending | 尚未執行 |
| GitHub Actions final head | pending | 尚未執行 |
| GitHub Actions post-merge `master` | pending | 尚未執行 |

所有 gate 都標記 pending；本檔案不陳述任何未執行的檢查為通過。

## Exact gates（placeholder；執行前依實作 stage 確認）

```text
git diff --check
mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest test
mvn -f backend/pom.xml -B -Dtest=PostImageRepositoryTest test
mvn -f backend/pom.xml -B -Dtest=ImageValidatorTest test
mvn -f backend/pom.xml -B -Dtest=LocalMediaStorageTest test
mvn -f backend/pom.xml -B -Dtest=PostServiceTest,PostControllerTest,MediaControllerTest,AuthSecurityContractTest,ApiErrorContractTest test
mvn -f backend/pom.xml -B test
npm run lint
npm run build
docker build --progress=plain -t phark:sdd010 .
bash -n /tmp/phark-sdd010-runtime.sh
bash /tmp/phark-sdd010-runtime.sh
bash /tmp/phark-sdd010-runtime-finalize.sh
```

Host 無 JDK（與 SDD-009 相同）；focused/full backend commands 在 pinned
`maven:3.9-eclipse-temurin-17` container 或 production Docker build stage 執行。Node 使用
既有 Node 24/npm，不更換 package manager 或 lockfile。測試類名為 placeholder，實作 stage
若命名不同需同步更新。MediaContractTest（multipart required parts、`consumes` 兩支
handler、413 mapping、`handleMissingServletRequestPart`）與 SHA-256/byte-length serving
tests 會併入 `PostServiceTest,PostControllerTest,MediaControllerTest` 或獨立 contract test
class，依實作命名。Runtime evidence 只存於 `/tmp/phark-sdd010-runtime-*/evidence/`，不是
repository artifact，不含 production data 或 secrets。

## RED/GREEN 與 implementation evidence

- （TBD）Migration RED 先建立 V9 schema/one-to-one/cascade/CHECK（含 sha256 grammar）/
  backfill-empty cases；GREEN 後補 failed-migration rollback regression。
- （TBD）Validator RED 涵蓋 declared-vs-detected、magic bytes、`ImageIO`
  dimension-before-decode、pixel bounds 先於 full allocate、truncated/corrupt reject、
  5 MiB bounded read、1–4096、12,000,000 pixels、sha256 測量；Storage RED 涵蓋 server
  UUID key、key grammar、path lock、symlink/path-escape、byte-oriented `store/read/delete`
  （不洩漏 `Path`）、temp+atomic move、corrupt/missing file 與 compensating delete。
- （TBD）Create/read RED 涵蓋 multipart required parts、`consumes` 兩支 handlers、JSON
  分支保留、`VALIDATION_FAILED` vs `MALFORMED_REQUEST` 分流、`413`（含
  `MaxUploadSizeExceededException` mapping）、`handleMissingServletRequestPart`、
  type-mismatch `mediaId`、`INVALID_IMAGE`/`INVALID_MEDIA_ID`/`MEDIA_NOT_FOUND`、serving
  前 byte length/SHA-256 驗證與 public immutable cache。
- （TBD）Frontend composer/preview、lint/build 0 warnings 與 production bundle；runtime 真實
  upload/read/restart/cache/error/rollback/path 證據。

## Baseline 記錄

- `git rev-parse HEAD` → `e2c8e11330628baab457269ab21425fe22b5dc16`。
- Scaffold checkpoint 的 working tree 只新增 scoped docs
  （`docs/specs/010-media-attachments/{spec,design,tasks,verification}.md`）與
  `.team/reports/T-002.md`；未修改任何既有 durable docs、application code、tests、
  migrations、lockfiles 或 deployment templates。
- 本 checkpoint 只有「文件檢查」通過；其餘 gates 一律 pending，由後續 stage 補齊並回填
  本表。