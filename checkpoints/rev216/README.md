# rev216 — 單一 client JSON、Prism 直接重啟、初始化強制退出

接續 `a867d4072240f29bd1937d0245da0c44d703c304`（rev215），僅更新 `feature/generic-conversion-iyamato-corpus3`。本 checkpoint 記錄 rev216 已建置／已驗證的完整本地測試主檔與來源雜湊；完整 build kit 由對話附件交付。

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev216-local-test.1.jar`
- 4,232,688 bytes
- SHA-256 `74090805b9122516165245620fc947fabf02c2b1211f45aba5a5e11cd40e70b2`
- 精確 rev215 基底 SHA-256 `52b3471a623079f9c1d2d01d1f7d8a970fe2a1b96b4054e241a3dba88d4b5234`
- build kit SHA-256 `38c617c0e6d63b73f59a78fcfab948eb2cd4ed661a612176adcaa550b7466dc8`
- validation SHA-256 `60b3eebc1c0c86f75e56263551a020299d8daf220fef41258ee0728757986d68`

## 本次修改

1. 桌面／重啟設定統一到 `config/legacyforgebridge-client.json` 的 `conversionRestart`；schema 升至 3。舊 `legacyforgebridge-desktop.properties` 只做一次性遷移，成功後刪除，不再建立第二份使用者設定。
2. Cloth Config 現有遊戲內設定畫面新增「轉換與重新啟動」分類，可直接修改狀態視窗、自動 Prism 重啟、初始化強制退出、Prism 執行檔與資料根目錄。
3. Prism 重啟改成一次直接 `PrismLauncher --launch <instanceId>`；不再先送 `--show`。只有明確配置且驗證需要的資料根目錄才附加 `--dir`。不讀取／重播 Minecraft commandLine、登入 token 或帳號檔。
4. 不是 Prism 或無法可靠驗證 Prism 時，不自動關閉遊戲；沿用同一個獨立 Swing 狀態視窗提示手動重新啟動。
5. 確實需要重啟、轉換／cache／換檔計畫已落盤、helper ready、Prism 已驗證且仍在初始建構邊界時，`forceExitAfterConversion=true` 會先寫退出核准，再用 `Runtime.halt(0)` 直接停止目前 JVM；不 taskkill、不終止 Prism 或其他 Java。關閉此設定則用 `Runtime.exit(0)`；無法證明安全初始邊界時保留主執行緒正常退出後備流程。
6. 獨立狀態視窗維持 rev215 精簡版：預設只顯示狀態、模組 X/Y、轉換耗時；詳細資訊收合；只保留「開啟轉換診斷」與「取消自動退出／重啟」。沒有待重啟的正常啟動，以及自動重啟後的新遊戲，只有客端回報初始載入完成且 overlay 消失後才自動關窗。
7. `*-lfb.jar` 更新語義不改成無條件覆蓋：相同 SHA 不動；同名不同 SHA、未載入時原子替換；已載入時寫 pending，舊 JVM 退出後再替換。來源檔名／版本改變時，前一次 state 記錄的 LFB-managed 舊檔會退休；若已載入則退出後刪除。非 LFB 模組已占用同 Fabric ID 時仍為 `CONFLICT`，不覆蓋使用者模組。

## 驗證

- 本輪重新執行核心整合驗證：22,704 個 Java assertions，0 failures。
- Config216 12、PrismDirect216 7、ForceExit216 3、DesktopSafety216 45、RestartScheduler216 121。
- ConversionOutcome 56、FailureBoundary 16、FeedbackNumeric 21,902、FeedbackSafety 10、Nock 20、原始 iYAMATO source/rename/mutation 501。
- 9 個分離行程案例先前完整通過，包含真正 child JVM `Runtime.halt`、真正 ManagedSwapHelper、真正 Swing、C launcher recorder；成功案例只送一次 direct `--launch`，沒有 `--show`，owner/direct 模式沒有 `--dir`，測試敏感環境 sentinel 未轉交。
- 完整主檔 ASM：1,611 classes / 14,525 methods，0 BasicVerifier／已知內部引用錯誤；13,331 API preservation assertions 通過。
- 三次獨立 JDK 21.0.11 javac/ASM 建置得到完全相同主 JAR SHA-256。
- rev215 的 1,639 個基底 entries 全保留；候選 1,641；刪除 0。Energy 內嵌 JAR bytes 不變，語意 converter revision 不變。

## 驗證界線

沒有 Windows + 真實 Prism + 真實 Minecraft/Fabric/Mixin/Via／原服實機驗收；因此「原 Prism 主控台一定恢復 log 監控」仍需使用者端實測。使用者手動 `PrismLauncher.exe --launch <instance>` 能正常顯示狀態／log，是本次改回 direct launch 的現場依據。

本次不啟用 rev211 usingTick，也不補齊特殊投射物、完整裝填／連射、任意 GUI 等既有 PARTIAL 缺口。強制 `Runtime.halt` 會跳過其他模組 shutdown hook/finally，因此只允許在已驗證的初始化重啟邊界。

不修改 main/Bamboo／原始模組／伺服器／workflow，不 dispatch Actions、不開 PR/tag/release、不 force-push；提交含 `[skip ci] [skip actions]`。
