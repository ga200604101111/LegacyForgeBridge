# rev219 — 最終結果重新決定自動重啟資格，移除非權威 desktop 黏性 unhealthy

接續 `5bde66bb60fc3b416802119b90999d5d7bba3416`（rev218），只更新 `feature/generic-conversion-iyamato-corpus3`。不修改 main、Bamboo、原始模組、伺服器或 workflow。

## 使用者 rev218 現場

最新 session `7c38ecf5-a48e-4282-8b5a-813ff5e9e668`：
- Prism 已辨識，`forceExitAfterConversion=true`
- `PARTIAL 3`、`待重啟 3`、`unusableCount=0`
- 三個候選均 loaderSafe=true / STAGED
- `expected.count=3` 且三個目標 SHA 已保存
- 但最終仍是 `state=ERROR`
- 沒有 exit-approved、沒有 launch-claimed，因此 Runtime.halt 與 Prism --launch 根本未執行

這證明 rev218 的最終安全結果仍被某個較早的黏性 `healthy=false` 覆蓋。現場檔沒有記錄是哪一個 catch 設定它；本輪不猜測具體例外來源。

## 修正

1. 新增獨立的 `planUnsafe`。只有換檔計畫未知 action、目標/hash/path 衝突等安全關鍵問題才持久阻止最終自動重啟。
2. `finish()` 在讀完 authoritative conversion-state / ConversionOutcomeReport 後，用 `!planUnsafe` 重新建立健康狀態，再套用 final FAILED/BLOCKED/CONFLICT/unusable gate。
3. progress UI publish、pass diagnostics capture、conversion result diagnostic capture、intermediate status publish 的例外不再永久把 coordinator 標成 unhealthy。
4. 真正 final FAILED/BLOCKED/CONFLICT/unusable、swap-plan fault、helper/Prism/guard/取消/世界已進入等既有安全條件仍阻止退出。
5. rev218 preLaunch、rev217 15 分鐘 guard、rev216 單一 client JSON、direct Prism --launch、Runtime.halt 初始化安全邊界、Energy 與 gameplay bytes 都不變。

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev219-local-test.1.jar`
- 4,235,274 bytes
- SHA-256 `76f5a9fa3a77ac814d2abb66027463e035d37b0a9f0a09ebb18d0aaceba79126`
- exact rev218 base SHA-256 `bc1dc9c89c90de2d486441b11e961b556d80360f241c40fc5448a0925788134e`
- build kit SHA-256 `d6fd68c83b05195c31124fed91c2cf1d5ce9b9489cf4129a8b94482613478bf9`
- validation SHA-256 `6d082bf6d9a51f1883294bff367d2998c8212bf830ca028b0936ec48d74adaa1`

## 驗證

- Coordinator219Test：5 assertions / 0 failures；模擬 rev218 黏性 healthy=false，最終 safe PARTIAL/STAGED 必須恢復 READY；真正 planUnsafe 必須保持 ERROR。
- 真正 child JVM + Swing helper + ManagedSwapHelper + C launcher recorder：黏性 unhealthy -> final recompute -> INITIALIZATION_VM_HALT -> exit-approved -> 真正 halt -> 換檔 -> 一次 direct --launch；無 --show / owner-default 無 --dir。
- FeedbackNumeric 21,902、FeedbackSafety 10、Nock 20、iYAMATO source/rename/mutation 501、ConversionOutcome 56、FailureBoundary 16、DesktopSafety 46、PrismDirect 7、Coordinator218 8、PreLaunch218 2、Config216 12 全通過。
- 全主檔 1,612 classes / 14,528 methods；ASM BasicVerifier / 已知內部引用錯誤 0；13,354 API preservation assertions；206 external inherited members 未用真實遊戲 API 驗證。
- rev218 1,644 base entries 全保留；1,639 byte-identical、5 changed、0 removed；新增一份 rev219 build record。
- 兩次獨立 JDK21 javac/ASM 完整建置 byte-identical。

## 測試說明

首次 FeedbackNumeric 重跑漏了 legacy 1.7.10 fixture stubs，synthetic javac 在產品斷言前失敗；補回記錄的 oldhosts classpath 後 21,902 全過。首次 DesktopSafety 使用非 UTF-8 locale，測試路徑在產品斷言前失敗；C.UTF-8 重跑 46 全過。舊 ForceExit216 fixture 沒有設定 rev218 preLaunch 引入的 managerThread ownership，已不再是有效的早退單元測試；rev219 使用更強的 child-JVM 流程真正執行 Runtime.halt。

## 界線

沒有 Windows + 真實 Prism + 真實 Minecraft/Fabric/Mixin/Via/原服驗收。PARTIAL gameplay 缺口未變。Runtime.halt 仍只允許在已驗證初始化重啟邊界，會跳過其他模組 shutdown hooks/finally。

建置：JDK21 javac DesktopConversionSession + exact rev218 ASM identity overlay，不是 Gradle/Loom clean build。依賴沿用既有 ViaProxy artifact 10832672769 的 ASM/Gson class bytes，沒有新 Maven resolve。

提交含 `[skip ci] [skip actions]`；不 dispatch Actions、不開 PR/tag/release、不 force-push。
