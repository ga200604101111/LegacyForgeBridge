# rev248 — ViaFabricPlus 啟動預設版本

分支：`feature/generic-conversion-iyamato-corpus3`。
基準：`445da5d86f5141ce39026718a4ab56709fd49b4f`。
目標：Fabric 1.21.11 / Java 21 + ViaFabricPlus 4.4.15（API 6），連線至保持不變的 Forge 1.7.10 伺服器。

## 完整主 JAR

`legacyforgebridge-0.2.0-alpha.27-rev248-corpus4-local.31-startup-diagnostic.jar`

4,561,750 bytes；SHA-256 `480964a8cd00a7bc34417fcdadf3ffcacc7cdb464aa0bd477ade9d4015679bfe`。

精確基底：rev247 主 JAR，SHA-256 `db7627f77ddac1d84f2eff300f6397c9304971b56f0578d507d4a573944ae88a`。
這是完整累積主 JAR，不是輔助修補模組。沒有改造或內嵌 ViaFabricPlus。

## 問題與修正

舊 LFB 在 `onPlatformLoad` 中立即選擇 1.7.10。但上游先呼叫 addon entrypoint，之後才讀設定；到 `POST_GAME_LOAD` 中 `SettingsSave.postInit()` 還會套用已儲存版本，或切回原生版本。舊 LFB 又使用 `setTargetVersion(v1_7_6, true)`，其中 true 是 `revertOnDisconnect`，不是「永久儲存」；它會把原版本留作斷線還原。

本版保留 backend 初始化與版本變更回呼，改在公開的 `POST_FILES_LOAD` 事件執行一次啟動預設。使用 `setTargetVersion(v1_7_6, false)`，核對 API 與 backend 回讀均為 protocol 5，UI 可能显示為「1.7.6-1.7.10」。沒有持續 tick 強制、沒有重寫 settings.json 或 servers.dat，之後手動選版與每伺服器的獨立版本選擇保留。這是啟動的全域預設，不是每個連線永遠強制 1.7.10。

Minecraft 反射讀取延後至 POST_FILES_LOAD，不從早期入口初始化 Minecraft 類別。回呼若在其他執行緒，透過 client Executor 轉回主執行緒，執行前重新檢查；已有世界、玩家、遊戲連線、整合伺服器、VFP play 連線或連線畫面時跳過，不切換進行中的遊戲。未知 API、讀取失敗及回讀不符會記錄 FAILED / VERIFY_FAILED；不偽造成功，不反覆重試。

稀疏啟動記錄以 `LFB_PROTOCOL_STARTUP` 開頭。每次新移動 capture 只增添一筆 `PROTOCOL_STARTUP_CONTEXT`，含啟動前後與目前選版；明確標示它是全域選版，不是特定連線的協議或伺服器因果證明。

## 跳躍邊界

本版不是跳躍修復。保留 rev247 的落地後正速度標籤、rev245 的關聯核心、來源事件、落地粒子與溫泉修正；不取消或抵消任何速度。沒有擴大逐幀捕捉量，也未重跑使用者舊 logs.zip 來冒充新實測。

ViaFabricPlus 的版本協議與舊版 client 行為適配，不等於完整重現所有 Forge 模組語義。VFP/LFB 的移動與來源跳躍互動是可調查方向，但現有資料不能指定責任層。原生 Forge 1.7.10、同服同角色同裝備的對照仍缺少；三格半是刻意障礙，不能據其集中高度證明半磚 bug，也沒有五格反作弊限高證據。不要再只要求相同障礙場景大量重跳。

## 已實際執行的驗證

- 成品中的真實 LFB entrypoint / backend / startup helper，配合明確 VFP API／載入順序／Minecraft／logger／session test doubles：22 情境、241 斷言通過；舊 rev247 的設定覆蓋與斷線還原兩個問題由同套 doubles 重現。
- 成品診斷回歸：20 情境、643 斷言；新 capture-context 4 斷言。遊戲、Fabric、來源事件及 writer 使用既有明確替身，不包進 JAR。
- 成品快取：25 斷言。沿用 rev247 測試，只於暫存副本把 artifact-version 預期改為 rev248，保留轉換語義版本預期不變。
- 真實 packaged DesktopHelper / Swing / Xvfb：外層主 JAR、內嵌 helper、內嵌 helper 的 2 倍縮放，各 145 檢查通過。
- ZIP 完整、9 個 Java 21 class 解析、1,715 個原有外層項目內容不變。另逐一核對 1,251 個 conversion/mixin/behavior 保護項目相同；唯一 trace logger 改動是版本字串與每 capture 的額外背景記錄。
- 獨立重新建構後完整 JAR 逐位元一致。內嵌 helper 只有診斷摘要的 artifact 版號字串更新，排版與行為保留。

沒有下載上游 VFP 二進位或 Maven 依賴；沒有完整 Gradle/Loom、真實 Minecraft/VFP 管線、原生 Forge 客戶端、原模組集合轉換或伺服器測試。上游 API 由固定原始碼核對，不等於使用者實機驗收。前述 rev246 轉換失敗仍未取得對應例外，不能宣稱這版解決該未知錯誤。

## 重現方式

```sh
python3 checkpoints/rev248/build_local.py /path/to/exact-rev247.jar /path/to/rev248.jar
python3 checkpoints/rev248/test_local.py /path/to/rev248.jar /path/to/new-report --base /path/to/exact-rev247.jar
```

測試需要 JDK21 與 Xvfb，沿用 `checkpoints/rev247/tests`。完整逐例結果放在對話的 source-tests ZIP，或由 test_local.py 重建。根 src 不是累積交付狀態，不能單獨編譯並稱為 rev248。

## 上游參考

固定版本：`ViaVersion/ViaFabricPlus@2f00a92851bed1bdab05e2465cadd9b21e4e6b7f`。`api-references.json` 記錄讀取的檔案 Git blob SHA。

關鍵路徑：ViaFabricPlusImpl.init、SaveManager.postInit、SettingsSave.postInit、ProtocolTranslator.setTargetVersion/injectPreviousVersionReset、LoadingCycleCallback、docs/USAGE.md。Minecraft 的 client/thread/ConnectScreen 簽章依 Yarn 1.21.11+build.4 官方文件核對。

僅使用指定分支與 `[skip ci] [skip actions]`。不開 PR、不建立 tag/release、不修改／觸發 Actions、不變更原模組或伺服器。
