# TCL 睡眠定時器（Android TV）

專為 TCL 85C8L Google TV 設計的睡眠定時 App，可選擇 **5 / 10 / 15 / 20 分鐘** 後自動讓電視進入待機。

## 原理
- 前景服務負責倒數計時。
- 倒數結束時透過無障礙服務（Accessibility Service）執行電源動作，讓電視進入待機。

## 建置
使用 Android Studio 開啟專案，或執行 `gradle assembleDebug`。

## 安裝步驟
1. 電視開啟「開發人員選項」與「USB/網路偵錯」。
2. `adb connect <電視IP>`，再 `adb install TclSleepTimer.apk`。
3. 到「設定 > 系統 > 無障礙」啟用本 App 的無障礙服務。
4. 開啟 App，選擇 5/10/15/20 分鐘即可。
