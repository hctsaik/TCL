# TCL 定時關機（Android TV / Google TV）

專為 TCL 85C8L Google TV（Android 14）設計的睡眠定時 App：選擇 **5 / 10 / 15 / 20 / 30 / 60 分鐘** 後讓電視關閉螢幕/待機（通常會關閉螢幕/待機，請以立即測試實測）。

## 原理
- 倒數結束時間存於 SharedPreferences，並以 `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` 排程，**App 被系統殺掉也會照常關機**。
- 前景服務只負責通知列顯示剩餘時間（每分鐘更新）。
- 關機方式：無障礙服務執行 `GLOBAL_ACTION_LOCK_SCREEN`；約 1.5 秒後若螢幕仍亮且裝置管理員已啟用，改用 `lockNow()`。
- 兩者皆未設定時 App 會拒絕開始倒數；關機失敗時會跳出高優先通知並開啟 App 顯示錯誤。

## 建置
```bash
./gradlew assembleDebug   # 需 JDK 17 與 Android SDK（local.properties 設定 sdk.dir）
```
輸出：`app/build/outputs/apk/debug/app-debug.apk`

**固定簽章**：專案內附 `app/sleeptimer-debug.keystore`（store/key 密碼皆為 `sleeptimer`，alias `sleeptimer`），
每次重新建置的簽章相同，可直接 `adb install -r` 覆蓋更新，設定不會遺失。此金鑰公開，僅供個人自用，請勿用於上架。

## 安裝（用電腦 adb，約 10 分鐘）

### 1. 準備電腦
1. 下載 Google 官方 **Platform-Tools**：<https://developer.android.com/tools/releases/platform-tools>（Windows / Mac 各有版本），解壓縮。
2. 從 Releases 下載 `TclSleepTimer.apk`，放進解壓後的 `platform-tools` 資料夾。
3. 開啟終端機並切到該資料夾：
   - **Windows**：在資料夾空白處按 Shift+右鍵 →「在此開啟命令視窗 / 終端機」（或開 `cmd` 後 `cd` 過去）。下列指令的 `adb` 請打成 **`.\adb`**。
   - **Mac**：開「終端機」，輸入 `cd ` 後把資料夾拖進視窗按 Enter。指令請打成 **`./adb`**。第一次若被 Gatekeeper 擋下（無法驗證開發者），到「系統設定 > 隱私權與安全性」按「仍要打開」後再執行一次。
4. 電腦與電視需連在**同一個 Wi-Fi / 網路**。

### 2. 電視開啟開發人員選項
1. 電視：**設定 > 系統 > 關於**，選到「**Android TV 作業系統版本號**」，用遙控器 **OK 連按 7 次**，出現「您已成為開發人員」。
2. 回到 **設定 > 系統 > 開發人員選項**，擇一：
   - **A. 無線偵錯（Android 11+，建議）**：開啟「無線偵錯」→ 進入後選「**使用配對碼配對裝置**」，畫面會顯示 `IP:配對埠` 與 6 位數配對碼。電腦執行：
     ```
     adb pair 192.168.x.x:配對埠      # 輸入電視上的配對碼
     adb connect 192.168.x.x:連線埠   # 連線埠是「無線偵錯」主畫面上的 IP:埠（與配對埠不同）
     ```
   - **B. 網路偵錯（部分機型）**：開啟「網路偵錯 / USB 偵錯」，查電視 IP（設定 > 網路），電腦執行 `adb connect 192.168.x.x:5555`，電視跳出授權視窗時選「允許」。
3. 執行 `adb devices`，看到一行 `… device` 即連線成功（顯示 `unauthorized` 請在電視上按允許）。

### 3. 安裝 App
```
adb install -r TclSleepTimer.apk
```
若電視跳出 **Play Protect** 警告，選「更多詳細資料 > **仍要安裝**」（本 App 未上架，屬正常）。

### 4. 一次設定權限
先查看目前已啟用的無障礙服務：
```
adb shell settings get secure enabled_accessibility_services
```
- 結果是 `null`（或空白）：直接執行下面指令。
- 結果**不是 null**：`settings put` 會**覆蓋**原有清單，請把原本內容與本 App 用 `:` 串接，例如 `原本內容:tw.sleeptimer/tw.sleeptimer.PowerOffService`。

```
adb shell settings put secure enabled_accessibility_services tw.sleeptimer/tw.sleeptimer.PowerOffService
adb shell settings put secure accessibility_enabled 1
adb shell pm grant tw.sleeptimer android.permission.POST_NOTIFICATIONS
adb shell dpm set-active-admin tw.sleeptimer/.AdminReceiver
```
（最後一行是備援用裝置管理員，可選。）

### 5. 確認
1. 在電視上開啟「定時關機」，確認上方顯示「**無障礙：已開啟**」（剛設定完可能先顯示「已設定但未連線」，等幾秒會更新）。
2. 按 **立即測試**，3 秒後電視通常會關閉螢幕/待機，**請以立即測試實測**你的機型。
3. 之後選分鐘數即可；按「取消」可停止。

### 疑難排解
- 顯示「無障礙：未開啟」或「已設定但未連線」很久：重新執行第 4 步的 `settings` 指令；或在電視手動到 **設定 > 系統 > 無障礙設定** 找到「定時關機」開啟。
- 側載 App 的無障礙選項若為灰色：設定 > 應用程式 > 定時關機 >「允許受限制的設定」，或執行 `adb shell appops set tw.sleeptimer ACCESS_RESTRICTED_SETTINGS allow`。
- **強制停止 App 或電視重新開機會清除進行中的倒數**，需重新設定。
- 設定完成後，建議回到開發人員選項**關閉「無線偵錯 / 網路偵錯」**以策安全。

## 快捷方式
- 語音：「Hey Google，開啟定時關機」可直接開啟 App。
- adb：`adb shell am start -n tw.sleeptimer/.MainActivity --ei minutes 10`（僅接受 > 0 的數值）
