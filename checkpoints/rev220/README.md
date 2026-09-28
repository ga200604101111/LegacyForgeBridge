# rev220 — UI 焦點、ViaLegacy 掉落物 Y、伺服器權威跳躍、1.7.10 裝備屬性部位

接續 `a95c31f457af599b1a8f635d335a67717998017f`（rev219），只更新 `feature/generic-conversion-iyamato-corpus3`。不修改 main、Bamboo、原始模組、伺服器或 workflow。

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev220-local-test.1.jar`
- 4,237,478 bytes
- SHA-256 `bc52ed22e9dbc9a6a8ede4eab7cfc425a0feae6c63dde71cb9253ef0cdcfeff6`
- exact rev219 base SHA-256 `76f5a9fa3a77ac814d2abb66027463e035d37b0a9f0a09ebb18d0aaceba79126`
- build kit SHA-256 `fbcdaa1ba6b3476da11b5c795eb52607f02b7dbdcff32b75be3c9c9a3c47a7f8`
- validation SHA-256 `862caff2dcd4b04353863b06a86f649cd1d781b29746ffac0f6c37d094e57033`

## 四項修正

1. **獨立狀態窗口**：建立時只做一次 `toFront()/requestFocus()`，明確 `setAlwaysOnTop(false)`；後續刷新不再搶焦點。舊 JVM 退出、換檔驗證成功並成功送出一次 Prism direct `--launch` 後，舊 helper/window 立即 dispose/退出；下一輪 JVM 的 preLaunch 自己建立新窗口。
2. **ViaLegacy 1.7.10 掉落物 Y**：新增精確 Mixin，只覆寫 `Protocolr1_7_6_10Tor1_8.realignEntityY` 的 ITEM 類型，且只在目標為 1.7.10 時保留來源 fixed-point Y。其他 entity type / target version 不改。上游目前對 ITEM 會減去約 0.12 block 的 height alignment；此窄修正避免現代客戶端出生點進入地板後向下一格沉落的現象，仍需使用者實機驗證。
3. **偶發跳高**：使用者 rev219 motion trace 證實 converted RPGTool LivingJumpEvent 先把本機 Y 速度從約 0.42 改為 0.57，同一跳仍在空中時伺服器又送來約 0.569859 authoritative velocity，該次 peakRise 3.607284 blocks。rev220 只在 converted client-side jump callback 返回後撤銷「向上的 Y 修改」；X/Z 修改保留，後續 server velocity packet 完全不攔截，讓 1.7.10 server 保持權威。
4. **裝備屬性部位**：source item 已有 proven armor slot 時，不再同時把 attribute modifier 掛到 MAIN_HAND；只註冊到對應 armor equipment group。非 armor item 的 MAIN_HAND modifier 保留。

## 驗證

- 核心回歸 22,593 assertions / 0 failures。
- Rev220StructuralTest 34 / 34。
- 真流程隔離測試：真正 child JVM `Runtime.halt(0)`、真正 ManagedSwapHelper、真正 Swing helper、C Prism argv recorder；swap verified、一次 direct `--launch`、舊 helper 在 launch submission 後關閉、沒有 persistent always-on-top。
- 全主檔：1,613 classes / 14,531 methods；ASM BasicVerifier / known internal errors 0；13,354 API retention assertions；新 Minecraft/Fabric member refs 0；206 external inherited refs 尚未以真實遊戲 API 執行驗證。
- 兩次獨立完整組裝 byte-for-byte 相同。
- rev219 1,645 個 base entries 全保留；candidate 1,647；removed 0；1,636 base entries byte-identical。
- Energy nested JAR SHA-256 前後均為 `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`。

## 界線

沒有在 Windows + 真實 Prism + 真實 Minecraft/Fabric/Mixin/Via + 原 1.7.10 server 完成 rev220 端到端驗收。掉落物、跳躍與窗口焦點均需要使用者現場確認。既有 PARTIAL gameplay 缺口不屬於本版範圍。

建置：JDK21 `javac --release 21` 編譯 DesktopHelper / ViaLegacyDroppedItemYMixin + exact rev219 hash-pinned ASM full-main overlay；不是 Gradle/Loom clean build。沒有新 Maven resolve。

提交含 `[skip ci] [skip actions]`；不 dispatch Actions、不開 PR/tag/release、不 force-push。
