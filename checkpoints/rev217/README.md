# rev217 — 修正 rev216 同一更新計畫被永久防循環鎖住

接續 `2614a96778caea6435078ed8bb1d757aced40140`（rev216），只更新 `feature/generic-conversion-iyamato-corpus3`。不修改 main、Bamboo、原始模組、伺服器或 workflow。

## 現場根因

使用者提供的 rev216 `desktop.zip` 最新工作階段 `ab0d20e3-f35e-4f6a-88e4-1138ad1e80dc`：
- `forceExitAfterConversion=true`
- `launcherDetected=PRISM`
- `launcher.useDefaults=true`
- 本輪仍是待重啟 3，但最後進入 `state=MANUAL`
- phase 為「已阻止重複自動重啟」
- 沒有 `exit-approved.properties`，也沒有 `launch-claimed`

全域 restart guard 仍是 18:50 的 `c836a07d-...`，plan key 與 23:04 最新工作階段相同。rev216 的 `RestartPlan.guardAllows` 對相同 key 永久拒絕，因此根本沒有走到 Runtime.halt 或 Prism 呼叫。

## 修正

- 防循環窗口固定為 900000 ms（15 分鐘）。
- 相同 plan key 與不同 plan key 都只在窗口內禁止再次自動重啟；窗口過後允許使用者再次啟動時重新嘗試。
- 剛由 LFB 自動拉起的新遊戲若仍回報同一待重啟計畫，15 分鐘內仍會被攔住，不會形成無限重啟。
- 既有「本輪不再需要重啟時刪除 restart-guard.properties」維持不變。
- Prism 呼叫、force-exit 判斷、轉換語意、Energy 與 gameplay runtime 都不改。

Prism owner/default 模式仍是精確 argv：`PrismLauncher.exe --launch <instanceId>`；不讀取／重播 Minecraft commandLine、登入 token 或帳號檔。

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev217-local-test.1.jar`
- 4,233,341 bytes
- SHA-256 `3891a4ce9a77aac0c22884c07b234b0fad1545dc0b287410ab5f9692e97ed529`
- 精確 rev216 base SHA-256 `74090805b9122516165245620fc947fabf02c2b1211f45aba5a5e11cd40e70b2`

## 驗證

- 兩次獨立 ASM 組裝 byte-identical。
- RestartGuard217Test：main 8/8，embedded helper 8/8。
- 使用者實際四小時舊 guard + 同 plan key：rev217 `guardAllows=true`。
- 修改 rev216 guard 期望後重跑核心回歸：22,694 assertions，0 failures。
- 針對本次症狀的分離行程流程：過期同 plan guard -> 真正 child JVM Runtime.halt -> exit-approved -> 真正 ManagedSwapHelper 替換 -> C Prism recorder 收到一次 direct `--launch`；沒有 `--show`、沒有 `--dir`。
- 全主檔 1,611 classes / 14,525 methods，ASM BasicVerifier／已知內部引用錯誤 0；13,349 API preservation assertions 通過，206 external inherited members 未用真實遊戲 API 驗證。
- rev216 1,641 個基底 entries 全保留，1,634 個 byte-identical，0 移除；只變更 RestartPlan、狀態訊息、版本識別、embedded helper 與 metadata，另新增 rev217 build record。

## 界線

沒有在 Windows + 真實 Prism + 真實 Minecraft/Fabric/Mixin/Via/原服上執行 rev217。分離流程是真正 child JVM halt、真正 swap helper、真正 Swing helper與 C 啟動器參數記錄器，不是真實 Prism 登入／主控台／log 監控。既有 PARTIAL 功能缺口未被本次更新補齊。

建置方式：JDK 21 + exact rev216 ASM patch，不是 Gradle/Loom clean build。依賴沿用 rev216 已核對的 ViaProxy artifact 10832672769 class bytes；沒有新 Maven resolve。

提交包含 `[skip ci] [skip actions]`，不 dispatch Actions、不開 PR/tag/release、不 force-push。
