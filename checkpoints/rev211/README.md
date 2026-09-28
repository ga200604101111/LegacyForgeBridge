# rev211 — SOURCE ONLY：持續使用 tick 的來源投影與事件前置界線

接續 `25fdf21b5cdfbed30289c0aee2b7c292d4070e85`（rev210），僅更新
`feature/generic-conversion-iyamato-corpus3`。

**這是已編譯、已跑測試的來源更新，不是可安裝版本。沒有完整主 JAR；沒有啟用遊戲內持續使用、裝填或連射回呼。ZIP 不可放入 mods，仍保留原有 rev209 主檔。**

## 本次實作

- `ClientFeedbackCompiler.compileUsingTick` 讀取精確的 Forge 1.7.10
  `onUsingTick(ItemStack, EntityPlayer, int)` 公開實例方法，沿來源繼承與 helper
  分析數值條件；拒絕 static/private、錯誤簽章、例外依賴及沒有可投影動作的回呼。
- 保留 rev210 的 float/double 回傳與 super/virtual 方法語義。精確
  `invokespecial net/minecraft/item/Item.onUsingTick` 才可使用官方空方法投影；
  來源可見覆寫優先，未知父類／世界／狀態操作不會被空方法捷徑繞過。
- `ClientFeedbackProgram.evaluateUsingTick` 接受明確的「事件後、遞減前」剩餘時間。
  未知事件結果回傳 DEFER；事件取消或剩餘時間 <= 0 不進入 callback；
  事件延長時間時保留原值，不把它裁成 duration，也不把它當成已使用時間。
  超出數值預算仍 DEFER。來源動作順序與既有 VM 預算保留。
- tick 使用獨立 schema 2，必須標示 `PlayerUseItemEvent$Tick` 與
  `AFTER_EVENT_BEFORE_DECREMENT`。既有 release/entity 保留 schema 1；
  普通 `evaluate()` 無法直接執行 usingTick。
- 正式 `ClientFeedbackCompiler.apply()` 已輸出 `pendingUsingTick`，明確標記
  `nativeRuntimeEnabled=false`、`REQUIRES_UPSTREAM_TICK_EVENT_BRIDGE`。
  **沒有將 tick 放進現有 runtime 讀取的 `items[].programs`。**
  既有 `compile()` API 仍只回傳 release/entity；未修改 `ClientFeedbackRuntime`、
  ConvertedBehaviorItem、Mixin 或原生遊戲回呼。

這一段完成的是「來源編譯／純 VM／待接線契約」，不是整個裝填功能。
`OptionalInt` 只是可信上游呼叫端提供的事件結果，不是安全證明，也不是這次已完成事件橋接。
未來呼叫端還必須核對事件後仍在使用的玩家／世界／手／stack／來源 class，重新取得
事件後的 duration、damage、maxDamage，處理換物品與取消，再接入原生 tick。
不能把事件前的數值當成事件結果，也不能把這次的合成測試當作跨模組事件准入證明。

## 原始語義依據

核對 MinecraftForge 官方 `1.7.10` 原始碼：

- `patches/minecraft/net/minecraft/item/Item.java.patch`：基底 onUsingTick 為空方法。
- `patches/minecraft/net/minecraft/entity/player/EntityPlayer.java.patch`：先呼叫
  ForgeEventFactory.onItemUseTick，再判斷 <= 0；只有正值才呼叫 onUsingTick，之後才遞減。
- `src/main/java/net/minecraftforge/event/ForgeEventFactory.java`：取消回傳 -1，
  否則使用事件可修改的 duration。該檔當次 Git blob SHA：
  `a8248255604df78ec78abe3369bd1b0282023c0f`。

參考：
https://github.com/MinecraftForge/MinecraftForge/tree/1.7.10

## 真正執行的測試

JDK 21.0.11；產品 `javac --release 21`；合成來源 `--release 8`。

| 類別 | 結果 |
| --- | --- |
| 新增 usingTick 來源／JVM 對照 | 10,469 assertions，0 failures |
| 實際 apply() 的隔離檔案系統打包 | 候選 29 assertions 通過；基底 9 通過 |
| 原封不動 rev210 數值回歸 | 基底與候選各 21,902 通過 |
| 原封不動 FeedbackSafetyTest | 基底與候選各 10 通過 |
| 保留既有產品 API | 80 assertions，0 移除／不相容項 |
| 新增合成來源驗證 | 204 次 fixture 方法 ASM BasicVerifier 檢查 |
| 候選產品驗證 | 11 class、77 methods，ASM BasicVerifier 通過 |
| 可重現性 | 兩個獨立目錄的產品 javac class 全部相同 |

新增回歸涵蓋：第一 tick、充能／週期門檻、來源除數 20→40 變異、所有來源 class
改名、來源繼承、float/double helper、super 與 virtual 差異、精確平台空方法、
剩餘時間被事件縮短／延長／取消、未知事件、世界端分支、未知狀態前後的動作前綴、
錯誤 schema／事件 phase、static／錯 arity、VM 動作預算與重複打包。

**基底 rev210 的新測試僅重現 1 項「缺少 compileUsingTick 入口」的預期失敗；
不是宣稱基底有 10,469 個 bug。** 原有 release/entity 的實際打包 JSON 逐位元組相同。
斷言數不是支援武器、技能或已完成裝填的數量。

`compile-only/ConversionContext` 每個方法都拋錯，僅用於編譯。
`recording-context/ConversionContext` 是獨立的檔案系統測試替身，讓真正 apply()
方法讀寫合成來源與暫存契約；**不是 LegacyConversionEngine，也沒有 DataFixer。**
`fixture-hosts` 是記錄揮手的合成 Minecraft 類別，不是真實遊戲 API。
所有 test hosts、編譯宣告與依賴均不屬於產品 class。

未跑完整橋接器 class audit、舊 3,886 項全產品回歸、原始 iYAMATO/Bamboo/RPGTool
corpus、Gradle/Loom clean build、Minecraft/Fabric/Mixin/Via 或原服驗收。

## 來源與依賴

完整還原並驗證 rev210 的 35 份 checkpoint 檔案（XZ SHA-256
`adcaecd1b2f2368bbb66faa5a717cf723a52b7581aebed89a25993d3d4b56828`）。
這裡 `baseline/` 是精確 rev210 的兩份產品來源，不是根目錄舊 src。
另外只為讀取 runtime 與舊 pass 還原了 rev209 前四個分片中 14 份完整、逐檔 hash
匹配的項目；沒有完整 rev209 的 70 份 delta 或 269 檔累積 kit，未宣稱其 XZ 全檔 hash
已驗證。本次沒有用缺少的累積來源建立主檔。

真實 ASM/Gson 338 個 class bytes 從既有 ViaProxy artifact **10832672769** 擷取，
與來源 JAR 逐 class 比對相同；不是 Maven clean resolve，也沒有新 workflow。
完整來源與抽取 SHA 見 `validation/dependency-provenance.json`。

## 還原與重跑

```sh
python checkpoints/rev211/unpack.py --output ../lfb-rev211-source-only
cd ../lfb-rev211-source-only
python verify.py --classpath /path/real-asm-and-gson.jar --work ../rev211-verify-new
```

多個 JAR 的 classpath 用作業系統分隔符（Linux `:`，Windows `;`）；需 ASM
core/tree/commons/analysis 與 Gson。依賴不包含在來源包中。輸出必須為 kit 外的新目錄。
驗證器核對 source-sha256.json 與獨立釘住的 rev210 baseline hashes，實跑基底／候選，
並確認來源與依賴未被修改。`source-changes.diff`、`changes.json` 顯示兩份產品的精確變更。

## 尚未完成／未來整合界線

跨模組 PlayerUseItemEvent.Tick 的實際事件處理、事件後狀態與使用生命週期驗證、
原生 usageTick 接線、完整裝填／連射視覺音效與 NBT 流程、特殊投射物、其他能力、
任意 GUI 以及原服驗收仍未完成。沒有修改傷害、爆炸、耗彈、耐久、跳躍或速度封包。

完整主檔尚需既有精確 rev209 基底：
`legacyforgebridge-0.2.0-alpha.27-rev209-local-test.1.jar`，
SHA-256 `f9cce93d55b745a5de286351a70304ed051157caebd505b0dffdcb2607fb19d7`。

要整合時先還原完整 rev209 累積 kit，再依 rev210 與本次 changes.json 的前後 hash
依序替換兩份來源，保留所有其他累積修正。還需新的主檔版本／轉換指紋、快取失效與
完整回歸，**不可用舊 rev209 builder 將這些新來源包成同一個 rev209 名稱／指紋。**

本次只建立來源 checkpoint；不修改原模組／伺服器／main／Bamboo／workflow，
不 dispatch Actions，不開 PR、tag 或 release；提交包含 `[skip ci] [skip actions]`。
