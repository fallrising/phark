# 001：時間線游標分頁任務

## 階段 A：規格與設計

- [x] 定義問題、範圍與驗收條件。
- [x] 定義 page response 與錯誤契約。
- [x] 選定 `(created_at, id)` keyset cursor。
- [x] 定義前端各欄 state 與刷新行為。

## 階段 B：Backend vertical slice

- [x] 新增 `PostPage`、內部 cursor model 與 codec。
- [x] 將 Repository 查詢改為 bounded keyset query。
- [x] 新增 composite indexes。
- [x] Controller 接受 `limit`、`before` 並回傳 page object。
- [x] 增加正常、邊界與錯誤測試。
- [x] 更新 API 文件。

## 階段 C：Frontend vertical slice

- [ ] 更新 TypeScript page contract 與 API client。
- [ ] 每欄保存獨立 cursor、loading 與 error state。
- [ ] Column 新增「Load more」互動。
- [ ] 發文成功後重設三欄第一頁。
- [ ] 更新使用者文件。

## 階段 D：整合驗證

- [ ] Backend tests 通過。
- [ ] Frontend lint/build 通過。
- [ ] Docker build 通過。
- [ ] 審查 migration、API contract 與空/錯誤狀態。
