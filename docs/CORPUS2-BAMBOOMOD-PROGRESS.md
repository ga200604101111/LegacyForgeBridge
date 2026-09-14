# Corpus #2 — BambooMod 通用轉換進度

來源：`BambooMod` / Minecraft 1.7.10 ver2.6.8.5
來源 SHA-256：`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

本文件只記錄已由通用 analyzer / compiler 與完整 CI 實際證明的進度；未完成的項目不標成成功。

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
- Bamboo Manifest 未宣告 `FMLCorePlugin`，因此 dormant transformer classes 不再錯誤 BLOCK 全包。
- unrelated namespace synthetic fixture 已驗證 helper-wrapped registration 泛化。

GitHub CI tested source checkpoint：`279beefb13ed8428f869bef8b3168ec71bdba686`。

## P0 checkpoint B — lifecycle extraction

狀態：CI GREEN；已從中斷 checkpoint 恢復成正式 source commit。

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

狀態：CI GREEN；已從中斷 checkpoint 恢復成正式 source commit。

`LegacyRegistryAnalyzer` 新增 source static field identity binding，Bamboo exact corpus 可證明：

- static field → registry identity：104

`LegacyRecipeAnalyzer`：

- shaped crafting：134
- shapeless crafting：23
- smelting：5
- OreDictionary registrations：24
- fuel handlers：1
- analyzer diagnostics：0

總 crafting = 157。

目前 analyzer 已可重建 helper-wrapped recipes、`Object[]` pattern/key/ingredient、`ItemStack` output/count/meta，並用 source static field identity 解析 Mod 自己的 ingredient/output。

尚未把所有 recipe 直接宣告為「現代 JSON 已完成」，因為舊 `net.minecraft.init.Items/Blocks` 的 SRG static field 仍需要通用的 1.7.10 platform registry mapping。未解析 vanilla field 目前保留成 `FieldValue`，不猜測。

輸出 sidecar：`legacyforgebridge/recipe-analysis.json`。

Checkpoint B/C 經 checksum-verified recovery、完整 Gradle build、全部測試、remap、`verifyNoBundledAsm`、`verifyNoLongyuNamespace` 驗證後，落成 tested source commit：`12721a267ce02008e243ae47da57961946b6b396`。一次性 recovery 已從日常 CI 流程退役。

## P0 checkpoint D — generic Forge/FML event compiler

狀態：provenance / authority / safe target / PlaySound presentation path CI GREEN；ItemTooltip 與 RenderLiving Specials 仍在實作。

### D1 — event provenance / authority

已完成：

- `LegacyEventAnalyzer`：非執行式解析 `@SubscribeEvent` 與實際 `EventBus.register(Object)` 資料流。
- 同時辨識 Forge `MinecraftForge.EVENT_BUS` 與 FML `FMLCommonHandler.bus()`。
- 支援 `register(new Handler())` 與 constructor 內 `register(this)`，不用類名清單。
- 保留 event type、handler method/descriptor、bus、`@SideOnly`、priority、`receiveCanceled`、registration source site。
- `LegacyEventPolicy` 在 codegen 前分類 client presentation / server authoritative / contextual / unsupported，禁止 client 重複執行 legacy server authoritative gameplay event。
- `legacyforgebridge/event-analysis.json` 已接入 conversion pipeline，並輸出 execution policy 與 registration provenance。
- existing jump/fall/hurt compiler 已改用 registration provenance，不再靠鄰近 instruction 猜 listener。

目前 policy：

- client presentation：ItemTooltip、PlaySoundAtEntity、RenderLiving Specials Pre。
- server authoritative：LivingDrops、LivingDeath、AttackEntity、ItemCrafted、ArrowNock、ArrowLoose。
- contextual：PlayerTick、NameFormat，以及既有 jump/fall/hurt bridge。

### D2 — self-register constructor safety / Item target reuse

已完成：

- `LegacyEventHandlerConstructionAnalyzer`：minimal self-register listener 才允許精確 parent-constructor reconstruction；混有其他初始化時 fail closed。
- `LegacySelfRegistrationStripper`：只對 analyzer 已證明的 constructor，精確移除 Forge/FML `EventBus.register(this)` 指令序列，保留 constructor 其他初始化。
- source Item 若以相同 constructor 自行註冊 event，event adapter 透過 bootstrap transaction 重用已建立的同一 Item instance，不再第二次呼叫 legacy constructor。
- regression 驗證 generated class 無舊 MinecraftForge/EventBus、bootstrap 對 source Item 只 `NEW` 一次、event target 由 `bootstrapItem()` 取得同一 instance。

CI 證據：

- tested source commit：`8c18dbb6b3b2d9717dfd25bf4a7dd496f69c61a2`。
- run `34819845295`：完整 build/tests/remap/artifact 成功後才落 source。
- clean read-only run `34820068364`：staging 退役後再次完整成功。

### D3 — registered Block → source ItemBlock behavior identity

狀態：CI GREEN。

已完成：

- `GenericContentPass` 的 `sourceItemBlockClass` / `registerBlock(..., ItemBlockClass, ...)` registry evidence 直接供 behavior compiler 使用。
- `LegacyBehaviorApi.Block` / `ItemBlock` 提供窄版 source constructor boundary。
- source `ItemBlock(Block)` constructor 取得 generated Block identity，不以 Bamboo 類名特判。
- self-register ItemBlock constructor 可保留 `setMaxStackSize` 等原初始化，只移除舊 EventBus 註冊副作用。
- unrelated namespace regression 驗證 constructor 參數非 null、初始化保留、event target 重用同一 source ItemBlock instance。

CI 證據：

- tested source commit：`b228eb8b548c3c9c70dbb12b338370e49a85ef68`。
- clean read-only run #49：`34823189787`，完整 build/tests/remap/artifact 成功。

### D4 — PlaySoundAtEntity client-presentation callback

狀態：CI GREEN；exact Bamboo `ItemVillagerBlock` 所需路徑已具備。

Exact Bamboo source bytecode 已重新以 SHA-256 對照 JAR 驗證。`ItemVillagerBlock` handler 的真實語義為：

- `event.entity instanceof EntityPlayer`
- 讀玩家 head slot `func_71124_b(4)`
- head stack item 必須是 listener 本身 (`stack.getItem() == this`)
- 只處理 legacy sound `game.player.hurt`
- 依 health `% 3` 將 `event.name` 改成 `mob.villager.idle` / `mob.villager.hit`

已完成兩層轉換：

1. **source callback compiler/runtime**
   - `PlaySoundAtEntityEvent` 映射到窄版 Event API。
   - 保留 legacy Forge 語義：`name` 可改、可 cancel；`volume` / `pitch` 僅讀，source 修改不回寫。
   - presentation callback 不 commit Snapshot gameplay state。
   - unrelated ItemBlock fixture 使用與 Bamboo 等價的 branch / equipment / sound-name 行為。
   - tested source commit：`41bd334ab3e18162b11ec7ce7da6475e9f0e31f4`。
   - clean read-only run #53：`34823988742`。

2. **modern playback boundary / Via mapping**
   - `ViaLegacySoundMappings` 從目前 active 1.7.10 Via protocol pipeline 建立可逆 sound identifier path；沒有內建 Bamboo sound 對照表。
   - 普通 ViaVersion mapping 與 ViaBackwards mapping 按 mapping-data 語義選擇正/逆方向；沒有 FullSoundMappings 的 ViaLegacy 1.7→1.8 stage 自然跳過。
   - legacy endpoint 將 `minecraft:game.player.hurt` 還原為 Forge 1.7.10 看見的 `game.player.hurt`；replacement 再沿同一 stages 反向映回 modern SoundEvent。
   - `LegacyLocalPlayerSoundMixin` 僅在 1.7.10 target、本地玩家播放、且存在 source `sound` event 時介入；有 recursion guard；任一 mapping 缺失時 fail closed、保留原聲音。
   - exact mixin target descriptor 由 bytecode regression 鎖定：`LocalPlayer.playSound(SoundEvent,float,float)`。
   - tested source commit：`77fb95fb6c96d24b8b6d7de6e719318e4fc6ff30`。
   - staging / write-enabled workflow 已退役。
   - clean read-only run #58：`34825581732`，完整 build/tests/remap/artifact 成功。

### D5 — ItemTooltip（下一段）

狀態：exact Bamboo bytecode 已拆解；尚未落 production code。

`ItemBambooPickaxe.onItemTooltip(ItemTooltipEvent)` 的已證明缺口：

- `ItemTooltipEvent.itemStack` / `toolTip`。
- `NBTTagList` compound-list 讀取：`func_74745_c()`、`func_150305_b(int)`。
- `NBTTagCompound.func_150295_c(...)` 與 short 讀取 `func_74765_d(...)`。
- source `BambooEnchantment.idToEnchantMap` 是 `<clinit>` 以常數 id/name 建立的靜態表；`EnchantBase.getName()` 只是回傳 constructor 存入的 name，因此可做非執行式 constant table extraction，不需搬整套 enchant gameplay class。
- legacy `StatCollector.translateToLocal` 需要映射到現有 translation boundary。

另外，`ItemBambooPickaxe` constructor 含 IIcon array、ChestGenHooks loot registration 與 self EventBus registration，因此 ItemTooltip target 不能粗暴執行整個 legacy constructor；必須使用 proven presentation-target state / constructor sanitization，或以 declarative tooltip IR 取代 runtime constructor，不能猜。

## 目前 Bamboo candidate 狀態

仍為 `PARTIAL`，不得交付使用者實機測試：

- modern skeleton：42 Item / 63 Block
- lifecycle source registrations：42
- crafting：157
- smelting：5
- OreDictionary：24
- fuel：1
- compiled source item behaviors：27
- event provenance / authority / safe target：CI GREEN
- registered Block → ItemBlock behavior bridge：CI GREEN
- PlaySoundAtEntity source callback + modern playback boundary：CI GREEN
- ItemTooltip：exact corpus 已拆解，實作中
- original legacy classes 仍有 341 個；尚未達到 loader-safe 完整 port Definition of Done。

## 下一段

1. 完成 generic ItemTooltip client-presentation adapter：event stack/list、NBT compound-list、source-proven constant name table、legacy translation boundary。
2. 針對 `ItemBambooPickaxe` 的複雜 constructor，建立 fail-closed presentation event target 方案；不得執行 ChestGenHooks 或其他 legacy global side effects。
3. 擴充 RenderLiving Specials client presentation。
4. 建立通用 Minecraft 1.7.10 vanilla registry SRG mapping，讓 recipe ingredient/output 可完整現代化。
5. 真正輸出 shaped/shapeless/smelting recipe JSON、OreDictionary tags、fuel adapter。
6. 進入 P1：Block behavior、BlockEntity/Inventory/NBT、EntityType/DataWatcher、Menu/GUI。

只有當必要 gameplay/render/runtime 語義完成、舊 class 已安全移除/隔離且完整 CI + corpus regression 通過後，才產生 Bamboo 實機測試 JAR。
