# Corpus #2 — BambooMod 通用轉換進度

來源：`BambooMod` / Minecraft 1.7.10 ver2.6.8.5  
來源 SHA-256：`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

本文件只記錄已由通用 analyzer / compiler、來源證據與完整 CI 實際證明的進度；未完成的項目不標成成功。若目前工作區沒有 exact corpus JAR，公開 source semantic cross-check 與 unrelated-namespace regression 不會冒充 exact-JAR regression。

## 不可回歸基線

- RPGTool alpha.25 已由使用者實機確認完整可玩。
- alpha.26 僅增加舊版武器攻擊速度 Tooltip 隱藏規則，完整 CI 已通過。
- Corpus #2 不得加入 Bamboo modid、Bamboo 類名／registry ID 專用 production 分支。
- 無法證明的舊版語義一律 fail closed；不得用近似值或名稱猜測補齊。

## P0 checkpoint A — generic registry / dormant CoreMod

狀態：CI GREEN。

已完成：

- `LegacyRegistryAnalyzer`：直接與 helper-wrapped `GameRegistry.registerItem/registerBlock`。
- exact Bamboo corpus：42 Item + 63 Block，共 105 個註冊。
- Generic `converted-content.json` 與現代 Item/Block registry skeleton。
- Registry allocation/constructor 證據直接供 `LegacyBehaviorCompiler` 使用。
- Bamboo source item behavior 從 0 提升到 27 個已編譯 item callback program；失敗項目已收斂為具體 API 缺口，而不是身份找不到。
- `LegacyCoremodActivationAnalyzer`：區分 JAR 內存在 transformer 與實際啟用 CoreMod。
- Bamboo Manifest 未宣告 `FMLCorePlugin`，因此 dormant transformer classes 不再錯誤 BLOCK 全包。
- unrelated namespace synthetic fixture 已驗證 helper-wrapped registration 泛化。

GitHub CI tested source checkpoint：`279beefb13ed8428f869bef8b3168ec71bdba686`。

## P0 checkpoint B — lifecycle extraction

狀態：CI GREEN。

`LegacyLifecycleAnalyzer` 不載入或初始化來源 class，從 FML lifecycle / `<clinit>` 與 helper dataflow 還原：

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

狀態：CI GREEN。

`LegacyRegistryAnalyzer` 新增 source static field identity binding；先前 exact Bamboo corpus 可證明：

- static field → registry identity：104

`LegacyRecipeAnalyzer` 的先前 exact corpus 基線：

- shaped crafting：134
- shapeless crafting：23
- smelting：5
- OreDictionary registrations：24
- fuel handlers：1
- analyzer diagnostics：0

總 crafting = 157。

Analyzer 可重建 helper-wrapped recipes、`Object[]` pattern/key/ingredient、`ItemStack` output/count/meta，並用 source static field identity 解析 mod 自己的 ingredient/output。

輸出 sidecar：`legacyforgebridge/recipe-analysis.json`。

Checkpoint B/C checksum-verified recovery 後的 tested source commit：`12721a267ce02008e243ae47da57961946b6b396`。一次性 recovery 已退出日常 CI。

## P0 checkpoint D — generic Forge/FML event compiler

狀態：主要 client-presentation / authority / safe-target 路徑 CI GREEN。

### D1 — event provenance / authority

已完成：

- `LegacyEventAnalyzer`：非執行式解析 `@SubscribeEvent` 與實際 `EventBus.register(Object)` 資料流。
- 同時辨識 Forge `MinecraftForge.EVENT_BUS` 與 FML `FMLCommonHandler.bus()`。
- 支援 `register(new Handler())` 與 constructor 內 `register(this)`，不用類名清單。
- 保留 event type、handler method/descriptor、bus、`@SideOnly`、priority、`receiveCanceled`、registration source site。
- `LegacyEventPolicy` 在 codegen 前分類 client presentation / server authoritative / contextual / unsupported，禁止 client 重複執行 legacy server-authoritative gameplay event。
- `legacyforgebridge/event-analysis.json` 已接入 conversion pipeline。
- jump/fall/hurt compiler 已使用 registration provenance，不再靠鄰近 instruction 猜 listener。

目前 policy 包含：

- client presentation：ItemTooltip、PlaySoundAtEntity、RenderLiving Specials Pre。
- server authoritative：LivingDrops、LivingDeath、AttackEntity、ItemCrafted、ArrowNock、ArrowLoose。
- contextual：PlayerTick、NameFormat，以及既有 jump/fall/hurt bridge。

### D2 — self-register constructor safety / Item target reuse

狀態：CI GREEN。

- `LegacyEventHandlerConstructionAnalyzer`：只有 minimal self-register listener 才允許精確 parent-constructor reconstruction；混有其他未知初始化時 fail closed。
- `LegacySelfRegistrationStripper`：僅對 analyzer 已證明 constructor 精確移除 Forge/FML `EventBus.register(this)`，保留其餘初始化。
- source Item 若以相同 constructor 自行註冊 event，event adapter 透過 bootstrap transaction 重用同一 Item instance，不再第二次呼叫 legacy constructor。
- regression 驗證 generated class 無舊 MinecraftForge/EventBus、source Item 不重複 `NEW`、event target 重用同一 instance。

CI 證據：

- tested source commit：`8c18dbb6b3b2d9717dfd25bf4a7dd496f69c61a2`
- run `34819845295`
- clean read-only run `34820068364`

### D3 — registered Block → source ItemBlock behavior identity

狀態：CI GREEN。

- `GenericContentPass` 的 `sourceItemBlockClass` / `registerBlock(..., ItemBlockClass, ...)` registry evidence 直接供 behavior compiler 使用。
- `LegacyBehaviorApi.Block` / `ItemBlock` 提供窄版 source constructor boundary。
- source `ItemBlock(Block)` constructor 取得 generated Block identity，不以 Bamboo 類名特判。
- self-register ItemBlock constructor可保留 source 初始化，只移除已證明 EventBus 副作用。
- unrelated-namespace regression 驗證 constructor 參數非 null、初始化保留、event target 重用同一 source ItemBlock instance。

CI 證據：

- tested source commit：`b228eb8b548c3c9c70dbb12b338370e49a85ef68`
- clean run #49：`34823189787`

### D4 — PlaySoundAtEntity client-presentation callback

狀態：CI GREEN；先前 exact Bamboo `ItemVillagerBlock` bytecode 所需路徑已具備。

先前 exact bytecode 證明的 source 語義包含：玩家 head slot、listener 本身 item identity、legacy sound 名稱 branch、依 health `% 3` 替換 villager sound。

已完成：

- source callback compiler/runtime：保留 `name` rewrite / cancellation，presentation callback 不 commit gameplay snapshot。
- Via sound mapping：沿目前 active 1.7.10 Via protocol pipeline 做可逆 mapping，沒有 Bamboo sound hardcode。
- `LegacyLocalPlayerSoundMixin`：只在 legacy 1.7.10、本地玩家且存在 source sound event 時介入，mapping 缺失即保留原聲音。
- exact 1.21.11 target descriptor 由 bytecode regression 鎖定。

CI 證據：

- source callback tested commit：`41bd334ab3e18162b11ec7ce7da6475e9f0e31f4`
- clean run #53：`34823988742`
- Via playback tested commit：`77fb95fb6c96d24b8b6d7de6e719318e4fc6ff30`
- clean run #58：`34825581732`

### D5 — ItemTooltip client-presentation callback

狀態：CI GREEN。

已完成的 generic production surface：

- ItemTooltip source callback compiler/runtime。
- event `ItemStack` / tooltip list narrow adapter。
- legacy NBT compound-list / compound access adapter。
- `LegacyConstantNameTableAnalyzer` 可從 source constant initializer 還原 id→name 類表，不執行 source `<clinit>`。
- legacy translation 走既有 translation boundary。
- 對複雜或不安全 constructor 使用 presentation-only target / 已證明 sanitization，不為了顯示 Tooltip 執行 ChestGenHooks 等 legacy global side effects。
- invalid tooltip mutation fail closed；source temporary NBT 不會錯誤 commit 回 gameplay stack。
- Component/style preservation 有獨立 regression。

目前 CI regression 包含：

- `LegacyBehaviorTooltipEventCompilerTest`
- `LegacyBehaviorTooltipEventRuntimeTest`
- `LegacyBehaviorClientTooltipComponentsTest`
- `LegacyConstantNameTableAnalyzerTest`

### D6 — RenderLiving Specials / name-tag presentation

狀態：CI GREEN。

已完成：

- `RenderLiving Specials Pre` source callback compiler。
- modern runtime cancellation path。
- 1.21.11 renderer-state name-tag suppression boundary。
- source entity 不因 presentation callback 被持久修改。
- unknown entity-dependent behavior 不猜測。

CI regression 包含：

- `LegacyBehaviorRenderSpecialsCompilerTest`
- `LegacyBehaviorRenderSpecialsRuntimeTest`
- `LegacyNameTagBehaviorMixinBytecodeTest`

## P0 checkpoint E — vanilla stack / recipe / OreDictionary / fuel materialization

狀態：generic production + synthetic/source-shape regression CI GREEN；本輪尚未重新取得 exact Bamboo JAR，因此不宣稱 exact-JAR materialization coverage。

### E1 — 1.7.10 vanilla identity / ItemStack modernization

已完成：

- `LegacyVanillaRegistry1710`：1.7.10 `Items/Blocks` SRG static field → legacy registry string identity。
- `LegacyRecipeStackResolver`：只接受已證明的 1.7 `ItemStack` constructor 形狀；未知 constructor / identity fail closed。
- `LegacyVanillaStackDataFix`：直接以舊 registry string + metadata 交給 DataFixerUpper，取得 1.21.11 `id + components`；不維護第二套手寫 wool/log/dye flattening 表。
- vanilla durability metadata 可成為 `minecraft:damage`；0 damage 也顯式保留 exact ingredient 語義。
- converted mod subtype metadata 使用 LFB-owned `legacyforgebridge:legacy_meta`，不濫用現代 DAMAGE component。
- pre-flattening vanilla wildcard 無安全等價時 fail closed。

### E2 — shaped / shapeless / smelting JSON

已完成：

- `LegacyRecipeJsonMaterializer` 產生目前 1.21.11 shaped / shapeless / smelting JSON。
- `LegacyRecipeMaterializationPass` 將可完整證明的 recipe 寫入 candidate `data/<modid>/recipe/`。
- output / ingredient / pattern / metadata / Ore name 任一處無法證明時，整條 recipe 不寫入並保留 skipped provenance。
- recipe result 可保留現代 components。

### E3 — Forge 1.7 OreDictionary interoperability

已完成：

- `LegacyOreDictionaryConventions1710` 以 Forge 1.7 內建 OreDictionary category 為舊端 authoritative 名稱來源。
- 可安全對應的舊 category 轉到目前 conventional/platform tags。
- 本模組本地 Ore registration 與 platform category 做 union，不會把全域 `plankWood` / `logWood` 錯縮窄成單一模組內容。
- `dyeBlue`、`dustGlowstone` 等 Forge 1.7 啟動時本來就存在的 platform name 不再被誤判 missing。
- 非內建且沒有 source registration 的 Ore name 不猜。
- meta-specific local Ore entry 使用精確 component ingredient union，不放寬成整個 item 的所有 variant。

### E4 — generic IFuelHandler → stack-aware 1.21.11 fuel

狀態：CI GREEN。

已完成：

- `LegacyFuelHandlerAnalyzer`：非執行式解析實際被 `GameRegistry.registerFuelHandler(...)` 註冊的 handler。
- 第一版 admitted control-flow：`item == X`、`item == Item.getItemFromBlock(X)`、可選 `meta == N`、常數正 burn ticks、terminal/default 0；未知呼叫/算術/複雜控制流 fail closed。
- `LegacyFuelRuleMaterializer`：source registry identity → modern item ID / legacy metadata / modern damage predicate。
- converted mod 自己輸出 `legacyforgebridge/fuel-rules.json`；規則資料不硬編碼在 LFB。
- `LegacyFuelRegistry`：ordered stack-aware rule matching。
- `LegacyFuelValuesMixin`：在 1.21.11 `FuelValues.burnDuration(ItemStack)` 前查 legacy rule；沒命中才回 vanilla/Fabric 原路徑。
- 這是必要的，因為 1.21.11 vanilla `FuelValues` 底層只按 Item 儲存 burn time，無法表達 legacy meta/component predicate。

Bamboo 公開 source semantic cross-check（不是本輪 exact-JAR regression）：

- `straw` → 30 ticks，任意 metadata。
- `Item.getItemFromBlock(decoration_dir)` 且 metadata=4 → 270 ticks。
- 其他 → 0。
- unrelated-namespace synthetic fixture 使用相同 branch 形狀驗證 generic analyzer；另有 unsafe arithmetic/control-flow fail-closed regression。

最新 clean CI 證據：

- tested HEAD：`30bee04c1ab8ce2dedebf8fb094cb5879e716ff7`
- workflow：`corpus2-p0-verification` run #136
- run id：`34853389860`
- Full build and tests：SUCCESS
- remap / artifact upload：SUCCESS

### Exact-corpus evidence boundary

- `tools/corpus2-bc.part00~02` 是早期 checksum-pinned Git patch/recovery fragments，不是 Bamboo JAR，也不能作為 exact corpus input。
- exact source JAR SHA 仍固定為 `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`。
- 本輪工作區 / CI artifact 未保留該 exact JAR，因此尚未重新計算「157 crafting + 5 smelting 中實際 materialize 幾條」或 exact fuel output；這些數字在重新取得並驗證 SHA 前一律不宣稱完成。

## 目前 Bamboo candidate 狀態

仍為 `PARTIAL`，不得交付使用者實機測試：

- modern skeleton：42 Item / 63 Block
- lifecycle source registrations：42
- 先前 exact extraction：crafting 157 / smelting 5 / OreDictionary 24 / fuel handler 1
- recipe / OreDictionary / fuel **generic materialization pipeline**：CI GREEN
- exact Bamboo generated-resource coverage：等待重新取得 checksum-matched source JAR
- compiled source item behaviors：27（先前 exact corpus 基線）
- event provenance / authority / safe target：CI GREEN
- registered Block → ItemBlock behavior bridge：CI GREEN
- PlaySoundAtEntity：CI GREEN
- ItemTooltip：CI GREEN
- RenderLiving Specials / name-tag presentation：CI GREEN
- original legacy classes 仍有 341 個（沿用先前 exact audit；本輪未重新計數）
- 尚未達到 loader-safe 完整 port Definition of Done。

## 下一段

1. 重新取得 Bamboo exact JAR，先驗 SHA-256 必須等於 `bcceb588...f059b402`；再跑 end-to-end recipe/OreDict/fuel materialization coverage，逐條分類 written / platform-Ore / wildcard / unsupported，不用總數掩蓋 skip。
2. 進入 P1：generic Block behavior analyzer/compiler/runtime，優先處理 activation、placement/meta/state、drops、neighbor/tick 等可由 source bytecode證明的常見 1.7 Block surface。
3. BlockEntity / Inventory / NBT。
4. EntityType / DataWatcher / entity behavior。
5. Menu / GUI / container sync。
6. 必要 gameplay/render/runtime 語義完成、舊 class 安全移除/隔離、完整 CI + exact corpus regression 通過後，才產生 Bamboo 實機測試 JAR。

只有當 necessary semantics、loader safety 與 corpus regression 同時達標，才把 candidate 從 `PARTIAL` 提升為可交付測試狀態。
