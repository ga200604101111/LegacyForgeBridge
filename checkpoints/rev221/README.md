# rev221 — 保留 1.7.10 跳躍預測，只去重同一次 server velocity echo；修正 rev220 VerifyError

接續 rev220 checkpoint commit `50a92161db86fa223f1dba06b2f180df855f7829`，但**完整主 JAR 重新以 exact rev219 complete main 為二進位基底**，避免繼承 rev220 被錯誤重算的 StackMap frame。只更新 `feature/generic-conversion-iyamato-corpus3`；不修改 main/Bamboo/原始模組/伺服器/workflow。

## 為什麼撤回 rev220 的跳躍策略

rev219 實機 motion trace 證明 converted RPGTool LivingJumpEvent 會先在本地把起跳 Y 從約 `0.42` 提高到 `0.57`。單純保留這次本地預測時，boosted jump 的峰值約 `2.1658` blocks；異常高跳與稍後同一跳收到 server velocity `~0.569859` 或一個 legacy gravity/drag step 後的 `~0.480132` 高度相關，峰值升到約 `3.0378..3.6073` blocks。

因此 rev220「直接撤銷 client converted JumpEvent 的 upward Y」過度寬泛。rev221：
- 保留本地 converted jump prediction，維持即時 1.7.10-style jump。
- 只有 converted local callback 確實把 Y 向上增加 > 0.02 時才 arm prediction。
- 只在 1..4 tick 內、local player、airborne、同 entity、server packet 會向上 reboost current Y > 0.02，且 incoming Y 匹配 predicted Y 或 `(v0 - 0.08) * 0.98`（epsilon 0.004）時才視為同一次 prediction echo。
- echo 命中時保留 client 已自然衰減的 current Y，但接受 server X/Z；只消耗一次。
- 位置校正/respawn、damage/entity-status、explosion 會清掉 prediction。
- 其他 velocity packets 完全不攔截。

這是依使用者實機 trace 做的窄規則，不宣稱已在真實 1.7.10 server 完成 rev221 驗收。

## rev220 啟動 VerifyError

使用者真實 Prism 啟動 rev220 時，在 `LegacyFileLogger.initializeForLaunch()` 發生：
`java.lang.VerifyError: Bad type on operand stack`，exception handler stack 被寫成 Object 而非 Throwable。

根因是 rev220 identity/version overlay 對原本只需更新版本字串的類重新 COMPUTE_FRAMES，破壞 StackMapTable。rev221 對同長度版本常量使用 raw constant-pool byte replacement，不重算 `LegacyFileLogger` / `BuildInfo` / `FmlConnectionTrace` frames。

真正 JVM `-Xverify:all`：
- rev220 負對照可重現相同 VerifyError。
- rev221 `LegacyFileLogger` 通過 verifier；隔離執行後才因測試 classpath 沒有 FabricLoader 得到預期 NoClassDefFoundError。
- rev221 jump reconciler / replacement mixin / patched jump hook 亦通過 `-Xverify:all`。

## 沿用 rev220 非跳躍修正

- Swing 狀態窗口只在首次顯示時取得一次焦點，永不 persistent always-on-top。
- verified swap 後成功送出一次 direct Prism `--launch`，舊 helper/window 立即關閉；下一輪 preLaunch 建立新窗口。
- ViaLegacy target 1.7.10 + ITEM-only dropped-item Y 窄修正。
- proven armor item 只掛原 armor slot attribute，不額外掛 MAIN_HAND；非 armor 手持物保留 MAIN_HAND。

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev221-local-test.1.jar`
- 4,241,511 bytes
- SHA-256 `fc7cc8d6c97e0314eadedf0175dd75f85cff06a4540694a36aa8349815b81149`
- binary base exact rev219 SHA-256 `76f5a9fa3a77ac814d2abb66027463e035d37b0a9f0a09ebb18d0aaceba79126`
- build kit SHA-256 `a8f41d612eb5d85161e89e1315a4035ecd32807321eec94cb19bfcf05152ae2e`
- external validation SHA-256 `7392d0e4d01b84ab2b0266b83c3b06be8937afc404c31e52092bc4415c4aa1f2`

## 驗證

- 原核心回歸：22,593 assertions / 0 failures。
- `JumpEcho221Test`: 11 / 11。
- 真流程：real child JVM `Runtime.halt(0)` -> real ManagedSwapHelper -> swap verify -> C launcher argv recorder；一次 direct `--launch`、old helper closes、no persistent always-on-top。
- 兩次獨立完整組裝 byte-for-byte identical。
- whole-main audit：1,616 classes / 14,552 methods；0 known internal/BasicVerifier findings；13,354 API retention assertions；0 removed entries；0 new Minecraft/Fabric member refs。
- candidate entries 1,650；rev219 base entries 1,645；1,635 base entries byte-identical。
- nested Energy SHA-256 仍為 `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`。

## 界線

尚未在使用者 Windows + 真實 Prism + 真實 Minecraft/Fabric/Mixin/Via + 原 Forge 1.7.10 server 做 rev221 端到端驗收。jump echo matcher 是由捕獲到的現場 trace 導出，仍須現場確認。既有 PARTIAL gameplay 缺口不在本版範圍。

建置：JDK21 + exact rev219 hash-pinned javac/ASM overlays；不是 Gradle/Loom clean。沒有新 Maven resolve。提交含 `[skip ci] [skip actions]`；不 dispatch Actions、不開 PR/tag/release、不 force-push。
