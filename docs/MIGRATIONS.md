# Schema Migration Production Runbook

> 適用於 deck 應用（`deck` Compose project），基於 Flyway + SQLite。
> 最後更新：2026-07-30

---

## 不可變 Migration 撰寫規則

1. **Forward-only**：不撰寫 `undo` 或 `down` migration。已發布的 versioned migration
   視為 immutable，任何修正必須使用**新版本號**。
2. **命名格式**：`V<version>__<description>.sql`，置於
   `backend/src/main/resources/db/migration/`。
3. **Fail closed**：migration 預設應在 schema 與預期不符時失敗，不可為了讓
   部署通過而全面加入 `IF NOT EXISTS`。只有像 V2/V3 這類明確支援
   pre-Flyway schema、且有 upgrade tests 的相容橋接才可使用 idempotent DDL。
4. **不依賴 application code**：migration 只使用純 SQL，不得呼叫 Java repository
   或 service。
5. **不修改已發布 migration**：即使只改註解或空白，也會觸發 Flyway checksum
   驗證失敗。修正永遠走新版本。
6. **驗證**：本地執行 `docker build` 與 backend integration tests；CI 套用所有
   migration 到新的暫存 SQLite 以確認順序正確。

---

## 前置檢查與必要條件

```bash
# 安裝並確認 sqlite3 CLI（用於 backup、history 與 integrity check）
sudo apt-get update
sudo apt-get install -y sqlite3
sqlite3 --version

# 確認可寫入備份目錄
sudo install -d \
  -o deploy \
  -g deploy \
  -m 755 \
  /opt/apps/deck/backups

# 確認目前 compose project 狀態
sudo -iu deploy

cd /opt/apps/deck
docker compose ps
```

預期 `deck-app` **Up (healthy)**。

記錄目前映像供回滾參考：

```bash
APP_IMAGE_OLD=$(docker inspect deck-app \
  --format '{{index .Config.Image}}')
echo "${APP_IMAGE_OLD}"
```

---

## 步驟 1：停止應用（單一 instance，無 rolling update）

```bash
docker compose stop --timeout 30 app
```

確認 container 已停止，不再持有 SQLite 寫入鎖：

```bash
docker compose ps --all
# deck-app 應顯示 Exited
```

---

## 步驟 2：建立時間戳備份

```bash
TS=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP_FILE="/opt/apps/deck/backups/deck-${TS}.db"

sqlite3 /opt/apps/deck/data/deck.db \
  ".backup '${BACKUP_FILE}'"
```

**說明**：`.backup` 使用 SQLite Online Backup API，不需 `VACUUM` 或檔案複製，
可產生一致性 snapshot。

### 驗證備份完整性

```bash
sqlite3 "${BACKUP_FILE}" "PRAGMA integrity_check;"
```

預期輸出：`ok`

把本次復原資訊寫到與備份相同的持久目錄；若 SSH 中斷，不必依賴 `/tmp`：

```bash
{
  printf 'TS=%q\n' "${TS}"
  printf 'APP_IMAGE_OLD=%q\n' "${APP_IMAGE_OLD}"
  printf 'BACKUP_FILE=%q\n' "${BACKUP_FILE}"
} > "/opt/apps/deck/backups/deck-${TS}.restore.env"
```

---

## 步驟 3：指定並部署新映像

```bash
NEW_IMAGE="ghcr.io/fallrising/phark:sha-<40-hex-commit>"

printf 'Old image: %s\nNew image: %s\nBackup: %s\n' \
  "${APP_IMAGE_OLD}" "${NEW_IMAGE}" "${BACKUP_FILE}"

/usr/local/bin/deploy-deck "${NEW_IMAGE}"
```

`deploy-deck` 會安全更新既有 `.env` 的 `APP_IMAGE`，不會覆蓋 `APP_DOMAIN`。
腳本失敗時會嘗試重啟舊 image；只要本次 release 含 migration，仍須立即執行
下方「失敗處理」，因為 image rollback 不會還原 schema。

---

## 步驟 4：驗證

### 容器健康

```bash
docker compose ps
# deck-app 應顯示 Up (healthy)

docker inspect deck-app \
  --format '{{json .State.Health.Status}}'
# 預期: "healthy"
```

### 應用存活

```bash
docker exec deck-app \
  curl -fsS http://127.0.0.1:8080/actuator/health
# 預期: {"status":"UP"}
```

Compose 不 publish port 8080 到 host；因此本機 health check 從 container 內執行。
外部驗收另經 Traefik 執行：

```bash
APP_DOMAIN=$(sed -n 's/^APP_DOMAIN=//p' /opt/apps/deck/.env)
curl -fsS "https://${APP_DOMAIN}/actuator/health"
```

### Migration History

```bash
sudo sqlite3 /opt/apps/deck/data/deck.db \
  "SELECT version, description, installed_on, success
   FROM flyway_schema_history
   ORDER BY installed_rank;"
```

Runtime image 刻意不安裝 `sqlite3`；history 一律使用 host CLI 查詢掛載的 database。
所有 migration 的 `success` 欄位應為 `1`。若版本號與預期不符，表示
baseline 或 migration 順序有落差。

SDD-005 release 的預期 latest version 是 V4 `add accounts and ownership`。V4 新增
`accounts`、nullable `posts.author_account_id`、nullable
`replies.author_account_id` 與對應 indexes。升級 V3 或 legacy baseline 時不得依
`author` 字串建立 account；所有既有 ownership 必須保持 `NULL`，既有 row、ID、
timestamp 與 author snapshot 必須保留。

---

## 步驟 5：失敗處理

> **鐵則**：從不執行 `flyway clean` 或 `flyway repair`。失敗只走備份還原。

### 5a. 停止應用並保留失敗現場

```bash
docker compose stop --timeout 30 app

FAIL_DIR="/opt/apps/deck/backups/failed-${TS}"
sudo install -d -o deploy -g deploy -m 700 "${FAIL_DIR}"

# 移出而非刪除；restore 前 data/ 不可殘留舊 WAL/SHM。
for FILE in deck.db deck.db-wal deck.db-shm; do
  if [ -e "/opt/apps/deck/data/${FILE}" ]; then
    sudo mv "/opt/apps/deck/data/${FILE}" "${FAIL_DIR}/${FILE}"
  fi
done

sudo chown -R deploy:deploy "${FAIL_DIR}"

echo "Failed DB preserved at: ${FAIL_DIR}"
```

### 5b. 還原資料庫備份

```bash
# SSH 重連後先載入該次部署的實際檔案：
# . /opt/apps/deck/backups/deck-<timestamp>.restore.env

sudo install \
  -o 10001 \
  -g 10001 \
  -m 640 \
  "${BACKUP_FILE}" \
  /opt/apps/deck/data/deck.db

sudo sqlite3 /opt/apps/deck/data/deck.db "PRAGMA integrity_check;"
```

預期仍為 `ok`。此時 `data/` 只含還原後的 `deck.db`，SQLite 會在啟動時建立
對應的新 WAL/SHM。

### 5c. 回滾映像

```bash
# 還原 .env 中的 APP_IMAGE
sed -i "s|^APP_IMAGE=.*|APP_IMAGE=${APP_IMAGE_OLD}|" \
  /opt/apps/deck/.env

# 重新部署舊映像
docker compose pull app
docker compose up \
  -d \
  --no-deps \
  --wait \
  --wait-timeout 90 \
  app
```

### 5d. 最終驗證

```bash
docker compose ps
docker exec deck-app \
  curl -fsS http://127.0.0.1:8080/actuator/health
sudo sqlite3 /opt/apps/deck/data/deck.db \
  "SELECT name
   FROM sqlite_master
   WHERE type = 'table' AND name = 'flyway_schema_history';"
```

若備份建立於第一次導入 Flyway 之前，restore 後沒有 history table 是預期結果；
舊 image 必須恢復健康，且 posts/replies 資料必須仍可讀。

---

## 附錄 A：映像回滾 vs Schema 回滾

| 情境 | 動作 | 說明 |
|------|------|------|
| 應用程式 bug，schema 未變 | 只回滾 `APP_IMAGE` | 資料庫不須變動，直接切換 image |
| Migration SQL 失敗/不相容 | 還原 DB 備份 + 回滾 image | 兩者皆須執行；image 回滾不復原 schema |
| 部署後發現 migration 有邏輯錯誤 | 還原 DB 備份 + 回滾 image | 修正後用新版本部署，不走修補 |
| 已成功 migrate 但須退回舊 schema | 還原 DB 備份 + 回滾 image | 不手動 DELETE history rows |

摘要：**回滾 image 不等於回滾 schema**。Image 回滾只換容器執行檔；
schema 回滾必須從 `.backup` 還原資料庫檔案。

---

## 附錄 B：部署後報告範本

```text
=== Migration Deploy Report ===
Date:         2026-07-30T12:00:00Z
Old image:    ghcr.io/fallrising/phark:sha-abc123...
New image:    ghcr.io/fallrising/phark:sha-def456...
Backup:       /opt/apps/deck/backups/deck-20260730T120000Z.db
Integrity:    ok
Container:    Up (healthy)
Migration:    V1 ~ V4 success = 1
Result:       SUCCESS / FAILED → restored to abc123...
```

把完成後的報告保存為
`/opt/apps/deck/backups/deploy-report-${TS}.txt`，並連同對應的
`deck-${TS}.restore.env`、database snapshot 與部署記錄一起保留。
