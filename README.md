# TCL 定時關機（Android TV / Google TV）

專為 TCL 85C8L Google TV（Android 14）設計的睡眠定時 App：選擇 **5 / 10 / 15 / 20 / 30 / 60 分鐘** 後自動讓電視進入待機。

## 原理
- 倒數結束時間存於 SharedPreferences，並以 `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` 排程，**App 被系統殺掉也會照常關機**。
- 前景服務只負責通知列顯示剩餘時間（每分鐘更新）。
- 關機方式：無障礙服務執行 `GLOBAL_ACTION_LOCK_SCREEN`（等同按電源鍵 → 待機）；備援為裝置管理員 `lockNow()`。
- 兩者皆未設定時 App 會拒絕開始倒數；關機失敗時會跳出高優先通知並開啟 App 顯示錯誤。

## 建置
```bash
./gradlew assembleDebug   # 需 JDK 17 與 Android SDK（local.properties 設定 sdk.dir）
```
輸出：`app/build/outputs/apk/debug/app-debug.apk`

**固定簽章**：專案內附 `app/sleeptimer-debug.keystore`（store/key 密碼皆為 `sleeptimer`，alias `sleeptimer`），
每次重新建置的簽章相同，可直接 `adb install -r` 覆蓋更新，設定不會遺失。此金鑰公開，僅供個人自用，請勿用於上架。

## 安裝（建議：adb）
1. 電視：設定 > 系統 > 關於 > 連按「Android TV 作業系統版本」7 次，開啟開發人員選項。
2. 設定 > 系統 > 開發人員選項 > 開啟「USB 偵錯」/「網路偵錯（無線偵錯）」。
3. 查電視 IP（設定 > 網路），電腦執行：
   ```bash
   adb connect 192.168.x.x:5555     # 電視會跳出授權視窗，按允許
   adb install -r TclSleepTimer.apk
   ```
4. 一次設定好權限（免在電視上找選單）：
   ```bash
   adb shell appops set tw.sleeptimer ACCESS_RESTRICTED_SETTINGS allow
   adb shell settings put secure enabled_accessibility_services tw.sleeptimer/.PowerOffService
   adb shell settings put secure accessibility_enabled 1
   adb shell dpm set-active-admin tw.sleeptimer/.AdminReceiver     # 備援（可選）
   ```
   注意：`enabled_accessibility_services` 會覆蓋原有清單；若已有其他無障礙服務，請用 `:` 串接。

## 安裝（替代：檔案管理員）
用隨身碟 / 檔案管理 App 開啟 APK 安裝（需允許「安裝不明來源應用程式」）。
Android 13+ 對「側載」App 有 **受限制的設定**：無障礙選項可能是灰色。解法：設定 > 應用程式 > 定時關機 > 右上（或選單）「允許受限制的設定」，或用上面的 `appops` 指令。

**Play Protect**：安裝時可能顯示「已封鎖」或「不安全的 App」警告，選「仍要安裝 / 更多詳細資料 > 仍要安裝」即可（本 App 未上架，屬正常現象）。

## 首次使用
1. 開啟 App，確認上方顯示「無障礙：已開啟」（或「裝置管理員：已啟用」）。
2. 按 **立即測試**，3 秒後電視應進入待機，確認可正常關機。
3. 之後選擇分鐘數即可；畫面每秒顯示剩餘時間，按「取消」可停止。

## 快捷方式
- 語音：「Hey Google，開啟定時關機」可直接開啟 App。
- adb：`adb shell am start -n tw.sleeptimer/.MainActivity --ei minutes 10`（僅接受 > 0 的數值）
