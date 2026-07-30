# 004：一致的 API 錯誤任務樹

> 原則：每個階段獨立 commit 並推送；行為變更遵循 RED → GREEN → REFACTOR。

## A：規格與契約

- [x] **A.1 盤點現況**
  - [x] A.1.1 列出 controller、service、validation 與 Spring MVC error paths。
  - [x] A.1.2 確認現有 tests 只驗證 status。
  - [x] A.1.3 確認 frontend 丟棄 non-2xx body。
- [x] **A.2 定義 contract**
  - [x] A.2.1 定義 RFC 9457 common members 與 extensions。
  - [x] A.2.2 定義 stable error code/status mapping。
  - [x] A.2.3 定義 validation violation shape。
  - [x] A.2.4 定義 `X-Request-ID` 信任與替換規則。
- [x] **A.3 定義安全與驗收**
  - [x] A.3.1 定義未知 500 redaction。
  - [x] A.3.2 定義 frontend fallback。
  - [x] A.3.3 定義 test、Docker、runtime 與 CI quality gates。

## B：Backend vertical slice

- [ ] **B.1 RED — 錯誤契約 tests**
  - [ ] B.1.1 新增 reusable Problem Details assertions。
  - [ ] B.1.2 驗證 validation、domain 400 與 404。
  - [ ] B.1.3 驗證 malformed/type/method/media/route failures。
- [ ] **B.2 GREEN — Domain error model**
  - [ ] B.2.1 新增 `ApiErrorCode`。
  - [ ] B.2.2 新增 `ApiException` 與 validation violation model。
  - [ ] B.2.3 以 typed exceptions 取代 service `ResponseStatusException`。
- [ ] **B.3 GREEN — Global mapper**
  - [ ] B.3.1 實作 `ResponseEntityExceptionHandler` narrow overrides。
  - [ ] B.3.2 確保所有 response 使用 `application/problem+json`。
  - [ ] B.3.3 記錄且遮蔽 unknown 500。
- [ ] **B.4 REFACTOR — 回歸**
  - [ ] B.4.1 排序 violations 並移除重複 mapping logic。
  - [ ] B.4.2 執行 focused controller tests。
  - [ ] B.4.3 執行完整 backend tests。

## C：Request correlation

- [ ] **C.1 RED — Filter contract tests**
  - [ ] C.1.1 成功 response 沿用合法 inbound ID。
  - [ ] C.1.2 error header/body 使用相同 ID。
  - [ ] C.1.3 缺失或不合法 ID 產生安全 UUID。
- [ ] **C.2 GREEN — RequestIdFilter**
  - [ ] C.2.1 實作 allowlist、attribute、response header。
  - [ ] C.2.2 設定並在 `finally` 清除 MDC。
  - [ ] C.2.3 讓 error handler 使用 filter attribute。
- [ ] **C.3 REFACTOR — Logging safety**
  - [ ] C.3.1 驗證 newline、過長 header 不會被沿用。
  - [ ] C.3.2 驗證 500 log path 帶 request ID。
  - [ ] C.3.3 執行完整 backend tests。

## D：Frontend error handling

- [ ] **D.1 Client contract**
  - [ ] D.1.1 定義 `ApiProblem`、`ApiError`、`Violation`。
  - [ ] D.1.2 實作 Problem Details parse 與 malformed fallback。
  - [ ] D.1.3 四個 fetch functions 共用 handler。
- [ ] **D.2 UI wiring**
  - [ ] D.2.1 timeline/load-more 顯示安全 detail。
  - [ ] D.2.2 post composer 顯示安全 detail。
  - [ ] D.2.3 replies load/composer 顯示安全 detail。
- [ ] **D.3 Frontend gate**
  - [ ] D.3.1 執行 lint。
  - [ ] D.3.2 執行 TypeScript production build。
  - [ ] D.3.3 保留 network、unknown 與 malformed body generic fallback。

## E：文件與整合驗證

- [ ] **E.1 開發文件**
  - [ ] E.1.1 在 API docs 記錄 error schema/codes。
  - [ ] E.1.2 記錄 request ID header 與 debug 用法。
  - [ ] E.1.3 同步 roadmap 與本任務證據。
- [ ] **E.2 Production-like validation**
  - [ ] E.2.1 Docker multi-stage build。
  - [ ] E.2.2 成功 request correlation smoke。
  - [ ] E.2.3 400/404 Problem Details smoke。
- [ ] **E.3 CI 與交付**
  - [ ] E.3.1 推送所有階段 commits。
  - [ ] E.3.2 GitHub Actions 全綠。
  - [ ] E.3.3 更新 verification evidence 並完成 SDD-004。
