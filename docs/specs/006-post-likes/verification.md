# SDD-006 驗證紀錄

> 狀態：In Progress
> Branch：`agent/likes`

## Checkpoints

| Commit | 範圍 |
|--------|------|
| `1eb20d3` | Spec、design、風險與 54 項孫任務 |
| This checkpoint | V5 migration、composite uniqueness 與 like persistence |
| Pending | Viewer-aware timeline/profile reads |
| Pending | Authenticated idempotent like API |
| Pending | Frontend optimistic interaction |

## Required gates

| Gate | 狀態 | 證據 |
|------|------|------|
| Focused migration/repository tests | 通過 | 8 tests；0 failures、0 errors、0 skipped |
| Focused read/API/security tests | Pending | — |
| Complete backend regression | 通過（persistence checkpoint） | 150 tests；0 failures、0 errors、0 skipped |
| Frontend lint | Pending | — |
| Frontend production build | Pending | — |
| Multi-stage Docker build | Pending | — |
| Production-like runtime smoke | Pending | — |
| GitHub Actions final head | Pending | — |

完成時記錄 exact commands、test counts、image digest、runtime scenarios、workflow run/job URL
與 commit SHA；未實際執行的 gate 不標記為通過。

## Persistence checkpoint evidence

- Baseline（production change 前）：146 tests 通過。
- RED migration：3 個既有 upgrade path 皆顯示預期的 V5 缺失。
- RED repository：test compilation 因 planned `PostLikeRepository` / `LikeState` 缺失而失敗。
- GREEN focused（含 populated V4 → V5）：
  `mvn -f backend/pom.xml -B -Dtest=SchemaMigrationConfigTest,PostLikeRepositoryTest test`
  → 8 tests 通過。
- Regression：`mvn -f backend/pom.xml -B test` → 150 tests 通過。
- Host 無 JDK；以上 Maven commands 在 `maven:3.9-eclipse-temurin-17` container 執行。
