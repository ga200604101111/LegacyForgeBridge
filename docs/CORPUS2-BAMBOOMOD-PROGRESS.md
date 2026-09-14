# Corpus #2 — BambooMod 通用轉換進度

來源：`BambooMod` / Minecraft 1.7.10 ver2.6.8.5
來源 SHA-256：`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

本文件只記錄已由通用 analyzer 實際證明的進度；未完成的項目不標成成功。

## 不可回歸基線

- RPGTool alpha.25 已由使用者實機確認完整可玩。
- alpha.26 僅增加舊版武器攻擊速度 Tooltip 隱藏規則，完整 CI 已通過。
- Corpus #2 不得加入 Bamboo modid、Bamboo 類名／ID 專用行為分支。

## P0 checkpoint A — generic registry / dormant CoreMod

狀態：CI GREEN。

已完成：

- `LegacyRegistryAnalyzer`：直接與 helper-wrapped `GameRegistry.registerItem/registerBlock`。
- exact Bamboo corpus：42 Item + 63 Block，共 105 個註冊。
- Generic `converted-content.json` 與現代 Item/Block registry skeleton。
- Registry allocation/constructor 證據直接供 `LegacyBehaviorCompiler` 使用。
- Bamboo source item behavior 從 0 提升到 27 個已編譯 item callback program；失敗項目現在是具體 API 缺口，而不是身份找不到。
- `LegacyCoremodActivationAnalyzer`：區分 JAR 內存在 transformer 與實際啟用 CoreMod。
- Bamboo Manifest 未宣告 `FMLCorePlugin`，因此其 dormant transformer classes 不再錯誤 BLOCK 全包。
- unrelated namespace synthetic fixture 已驗證 helper-wrapped registration 泛化。

GitHub CI tested source checkpoint：`279beefb13ed8428f869bef8b3168ec71bdba686`。

## P0 checkpoint B — lifecycle extraction

狀態：本地 analyzer / compile / synthetic fixture 已通過；等待完整 GitHub CI checkpoint。

新增 `LegacyLifecycleAnalyzer`，不載入或初始化來源 class，從 FML lifecycle / `<clinit>` 與 helper dataflow 還原：

- mod entities：24
- TileEntity：11
- entity spawn rule：1
- GUI handler：1
- world generator：1
- dimension provider registration：1
- dimension registration：1
- dimension unregister：1
- provider unregister：1
- 總 lifecycle registrations：42
- analyzer diagnostics：0

Entity helper 的 registry name、numeric id、tracking range、update frequency、velocity flag 均由 source bytecode 推導；config-driven dimension id 保留為 unresolved source field，不猜值。

輸出 sidecar：`legacyforgebridge/lifecycle-analysis.json`。

## P0 checkpoint C — recipe / OreDictionary / fuel extraction

狀態：本地 analyzer / compile / synthetic fixture 已通過；等待完整 GitHub CI checkpoint。

`LegacyRegistryAnalyzer` 新增 source static field identity binding，Bamboo exact corpus 可證明：

- static field → registry identity：104

新增 `LegacyRecipeAnalyzer`：

- shaped crafting：134
- shapeless crafting：23
- smelting：5
- OreDictionary registrations：24
- fuel handlers：1
- analyzer diagnostics：0

總 crafting = 157。

目前 analyzer 已可重建 helper-wrapped recipes、`Object[]` pattern/key/ingredient、`ItemStack` output/count/meta，並用前述 source static field identity 解析 Mod 自己的 ingredient/output。

尚未把所有 recipe 直接宣告為「現代 JSON 已完成」，因為舊 `net.minecraft.init.Items/Blocks` 的 SRG static field 仍需要通用的 1.7.10 platform registry mapping。未解析 vanilla field 目前保留成 `FieldValue`，不猜測。

輸出 sidecar：`legacyforgebridge/recipe-analysis.json`。

## 目前 Bamboo candidate 狀態

仍為 `PARTIAL`，不得交付使用者實機測試：

- modern skeleton：42 Item / 63 Block
- lifecycle source registrations：42
- crafting：157
- smelting：5
- OreDictionary：24
- fuel：1
- compiled source item behaviors：27
- original legacy classes 仍有 341 個；因此尚未達到 loader-safe 完整 port 的 Definition of Done。

## 下一段

1. 將 checkpoint B/C 完整 CI 存檔。
2. 擴充 generic Forge event compiler：Crafted / LivingDrops / AttackEntity / LivingDeath / ItemTooltip / PlaySound 等核心事件。
3. 建立通用 Minecraft 1.7.10 vanilla registry SRG mapping，讓 recipe ingredient/output 可完整現代化。
4. 真正輸出 shaped/shapeless/smelting recipe JSON、OreDictionary tags、fuel adapter。
5. 進入 P1：Block behavior、BlockEntity/Inventory/NBT、EntityType/DataWatcher、Menu/GUI。

只有當必要 gameplay/render/runtime 語義完成、舊 class 已安全移除/隔離且完整 CI + corpus regression 通過後，才產生 Bamboo 實機測試 JAR。
