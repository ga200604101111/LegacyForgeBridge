# rev218 — preLaunch 狀態視窗、修正 PARTIAL/STAGED 被誤判 ERROR

接續 `62f573d0f9b31a7d4f74931632abad46e4fb2650`（rev217），只更新 `feature/generic-conversion-iyamato-corpus3`。不修改 main、Bamboo、原始模組、伺服器或 workflow。

## 使用者 rev217 現場根因

最新 desktop session `85bfdabf-b461-44bd-b6fc-da8a9cf93291` 同時顯示：
- `launcherDetected=PRISM`
- `forceExitAfterConversion=true`
- `PARTIAL 3`、`待重啟 3`、`unusableCount=0`
- 三個候選均為 loader-safe、STAGED、須下一輪啟動確認
- 但 coordinator 最後是 `state=ERROR`
- 沒有 `exit-approved.properties`、沒有 `launch-claimed`
- 同一舊 PID 後續又寫出 `game-ready.properties`，helper 因此可把目前遊戲自己的載入完成誤視為 relaunch 完成

rev217 的 desktop pass timing 在任一 pass 沒正常返回時直接把 coordinator `healthy=false`；這會先於 final loader-safe staged result 阻斷 halt。這是本輪修正的主要問題。

## 修改

1. `afterPass(success=false)` 只保存 pass 診斷，不再直接永久毒化 desktop coordinator。真正最終 FAILED/BLOCKED/CONFLICT、不安全／不可用結果仍由 finish 的 final state gate 阻止自動重啟。
2. `startupReady` 與 standalone helper 的 game-ready 判定都要求 terminal state 為 DONE 或 WARN。ERROR/MANUAL/READY/STOPPING 不能讓目前舊 PID 假裝成已重新啟動。
3. 新增 Fabric Loader `preLaunch` entrypoint：`dev.yinghuang.legacyforgebridge.LegacyForgeBridgePreLaunch`。它只提前建立 local desktop session、讀取設定與啟動獨立狀態視窗，不碰 Minecraft client/world。真正退出與世界狀態仍在正常 client/main 安全邊界。
4. manager thread 在真正 runManager 進入時才捕獲，所以 preLaunch 提前建立 session 不會破壞初始化 halt 的 thread ownership 檢查。
5. rev217 的 15 分鐘 loop guard、rev216 的單一 client JSON、direct Prism `--launch`、強制 halt 邊界、Energy 及 gameplay bytes 都保留。

Fabric Loader 官方的 preLaunch 是最早期 entrypoint，通常比 main/client 早數秒，但 Loader 在 entrypoint 前已完成本輪模組發現／解析；因此本輪才生成的 `*-lfb.jar` 仍需重啟一次才能加入下一輪 classpath。本版不做不受 Fabric 支援的動態 classpath 注入。

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev218-local-test.1.jar`
- 4,234,802 bytes
- SHA-256 `bc1dc9c89c90de2d486441b11e961b556d80360f241c40fc5448a0925788134e`
- exact rev217 base SHA-256 `3891a4ce9a77aac0c22884c07b234b0fad1545dc0b287410ab5f9692e97ed529`
- build kit SHA-256 `4995ec44f1f28cd1f483f87f394df918de308ed9a4302b7a2ae636230d22cb1a`
- validation SHA-256 `d9a2c0dc83703f166ff87070c4838126349cef970af6fe294b58b89e0a6a8a2d`

## 驗證

- 兩次獨立 exact-rev217 ASM 組裝 byte-identical。
- Coordinator218Test 8/8：故意注入 pass failure 後，loader-safe PARTIAL/STAGED 仍可 READY -> STOPPING -> INITIALIZATION_VM_HALT；ERROR current PID 不可 self-ready。
- PreLaunch218Test 2/2：FabricLoader 測試替身下，preLaunch 在 main 前建立 session/status；不是實際 Fabric Loader boot。
- ConversionOutcome 56、Config216 12、DesktopSafety216 46、RestartGuard217 8、ConversionFailureBoundary 16。
- FeedbackNumeric 21,902、FeedbackSafety 10、Nock 20、原始 iYAMATO source/rename/mutation 501 全通過。
- 真正 child JVM Runtime.halt + 真正 ManagedSwapHelper + standalone Swing helper + C launcher argv recorder：pass failure + PARTIAL/STAGED 仍成功 halt；exit approval 在 halt 前落盤；換檔完成；只送一次 direct `--launch`，沒有 `--show` / `--dir`。
- 全主檔 1,612 classes / 14,528 methods；ASM BasicVerifier/已知內部引用錯誤 0；13,349 API preservation assertions；206 external inherited member references 未以真實遊戲驗證。
- base 1,642 entries，candidate 1,644，removed 0；ZIP duplicate 0，integrity error 0。Energy nested JAR SHA 與 rev217 相同。

## 界線

沒有 Windows + 真實 Prism + 真實 Minecraft/Fabric/Mixin/Via/原服驗收。preLaunch 實際能提早多少秒、真實 Prism log 接管與 Windows halt/換檔仍需使用者端驗證。PARTIAL gameplay 缺口仍是 PARTIAL。

建置：JDK21 javac --release21 編譯三個產品來源 + exact rev217 ASM full-main overlay；不是 Gradle/Loom clean build。既有 ViaProxy artifact 10832672769 dependency bytes 沿用，沒有新 Maven resolve。

完整 source/build/test kit 由對話附件交付。本 checkpoint 保存 source diff、驗證摘要與交付記錄。提交含 `[skip ci] [skip actions]`；不 dispatch Actions、不開 PR/tag/release、不 force-push。
