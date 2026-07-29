# Phark 產品任務樹

> 最後更新：2026-07-29

狀態：`[x]` 完成、`[~]` 進行中、`[ ]` 待辦。優先級由 P0 到 P2。

## P0：可持續的時間線

- [x] **SDD-001 — Cursor-paginated timelines**
  - [x] bounded keyset API
  - [x] 三欄獨立載入更多
  - [x] Docker、runtime 與 CI 驗收
- [x] **SDD-002 — Replies and conversation threads**
  - [x] 規格、設計與 API 契約
  - [x] replies table、repository、service、tests
  - [x] inline conversation UI 與 reply composer
  - [x] Docker、runtime 與 CI 驗收
- [~] **SDD-003 — Schema migrations**
  - [x] migration history、legacy baseline 與 fail-closed 規格
  - [ ] 導入 Flyway
  - [ ] 將現有 schema 建立為 baseline migration
  - [ ] migration rollback/restore runbook
- [ ] **SDD-004 — Consistent API errors**
  - [ ] RFC 9457 Problem Details
  - [ ] validation field errors
  - [ ] request correlation ID

## P1：社交核心

- [ ] **SDD-005 — Accounts and profiles**（likes/reposts 的前置依賴）
  - [ ] handle、display name、bio
  - [ ] session authentication
  - [ ] author ownership 與 profile page
- [ ] **SDD-006 — Likes**
  - [ ] idempotent like/unlike
  - [ ] per-user uniqueness
  - [ ] timeline optimistic update
- [ ] **SDD-007 — Reposts**
  - [ ] repost/unrepost
  - [ ] original post attribution
  - [ ] timeline fan-out 規則
- [ ] **SDD-008 — Notifications**
  - [ ] reply/like/repost events
  - [ ] unread cursor
  - [ ] retention policy

## P2：探索與營運

- [ ] **SDD-009 — Search**
  - [ ] SQLite FTS5 index
  - [ ] cursor-paginated results
  - [ ] query limits
- [ ] **SDD-010 — Media attachments**
  - [ ] object storage abstraction
  - [ ] upload validation
  - [ ] image rendering
- [ ] **SDD-011 — Moderation and abuse controls**
  - [ ] rate limiting
  - [ ] content reporting
  - [ ] author/IP abuse signals
- [ ] **SDD-012 — Production durability**
  - [ ] SQLite online backup
  - [ ] metrics and alerts
  - [ ] deploy notifications

## 依賴關係

```text
001 pagination ──> 002 replies ──> 004 API errors
                         │
003 migrations ──> 005 accounts ──> 006 likes ──> 008 notifications
                         └─────────> 007 reposts ─┘
009 search、010 media、011 moderation 可在 accounts 後分流
012 durability 可與產品功能並行
```
