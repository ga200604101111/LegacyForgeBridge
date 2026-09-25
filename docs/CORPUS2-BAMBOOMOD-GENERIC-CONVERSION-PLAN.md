# Corpus #2 — BambooMod 通用轉換計畫

狀態：ACTIVE PLAN

工作分支：`feature/generic-conversion-bamboo-corpus2`

基底：RPGTool 實機驗收成功線（alpha.25 行為基線，alpha.26 僅增加舊武器攻擊速度 Tooltip 隱藏）。

第二個 corpus：`BambooMod` / `Minecraft1.7.10 ver2.6.8.5`

來源 JAR SHA-256：`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

## 核心原則

1. 不新增 `if (modid == BambooMod)`、Bamboo 類名表、物品 ID 表或技能／方塊效果硬編碼。
2. BambooMod 只作為第二個真實 corpus；每個新增能力至少要有一個不同 package / namespace 的合成 fixture 驗證泛化。
3. RPGTool 已實機驗收的 Item / Tooltip / NBT / use / release / hit / equipment / OBJ 能力是不可回歸基線。
4. 原始 1.7.10 class 不在現代客戶端直接載入或執行；只允許經驗證的語義提取、受限 callback codegen 或明確 runtime adapter。
5. 無法證明等價的行為要產生明確 diagnostic，不猜測、不靜默降級為另一種玩法。
6. 伺服器仍權威執行的效果不可在客戶端重複套用；client-only presentation 與 authoritative gameplay 必須分層。
7. 輸出命名維持 `原檔名-lfb.jar`；不憑空建立 zh_TW；發布 package namespace 維持 `dev.yinghuang`。

## 已記錄基線

目前 alpha.25/26 通用層已驗證：

- Forge 1.7.10 FML handshake / runtime transport 基礎。
- 舊 Item 建構資訊的部分提取。
- Tooltip、NBT、右鍵 use、UseAction、duration、release、hit、inventory/armor tick 的受限 source callback compiler。
- jump / fall / hurt 事件的受限事件轉換。
- legacy sword blocking / source bow-charge 使用生命週期。
- 裝備槽、基礎 armor attribute、裝備事件。
- OBJ item / wearable 幾何、UV、手持 transform、左右手與第三人稱 presentation。
- 舊武器攻擊速度屬性保留但 Tooltip 隱藏。
- 語言檔轉換、translation alias、generated modern entrypoint、candidate installer/cache。

RPGTool 實機結果：使用者已確認 alpha.25 功能可完美運行；因此後續改動不得破壞這條基線。

## BambooMod 初始盤點

目前 analyzer 對來源 JAR得到：

- classCount = 341
- unreadableClasses = 0
- Forge/FML reference patterns = 138
- Minecraft reference patterns = 823
- CoreMod markers = 2 (`IFMLLoadingPlugin`, `IClassTransformer`)
- OpenGL marker family = GL11
- mcmod.info / manifest 皆存在
- 現行 generic conversion 結果：`BLOCKED`
- 現行 generic codegen：items=0, creativeTabs=0；341 個 original legacy class 仍留在 candidate staging tree

這表示 Corpus #2 的主要價值不是再增加 RPGTool 類型的 Item 特例，而是把 generic semantic extraction 擴到世界內容層。

## Definition of Done

只有同時滿足以下條件才稱為「Bamboo corpus 可交付實機測試」：

- Generic profile 可以從原始 JAR 自動建立 Bamboo 所需的可支援 content identity，不依賴 Bamboo-specific pass。
- 所有被搬移到現代 runtime 的原始 class 行為都有 source-derived 語義或清楚 diagnostic。
- 無已知可執行舊 Forge class 被 Fabric loader 直接載入。
- CoreMod 是否實際啟用與其 transformer intent 已分析；不能因 JAR 內單純存在 transformer class 就誤判整包。
- Item / Block / BlockEntity / Menu / Entity / Recipe 等已支援 registry identity 可重建且命名穩定。
- Client renderer 與 authoritative gameplay 分層完成。
- `gradle build`、全部 unit/integration tests、remap、`verifyNoBundledAsm`、`verifyNoLongyuNamespace` 全通過。
- 對 Bamboo 原始 JAR 執行 corpus regression；輸出 deterministic。
- RPGTool corpus regression 全通過。
- 最後才產生 Bamboo 測試 candidate 給使用者實機驗收。

---

# P0 — 先把 Generic Profile 真正做成通用內容提取器

## P0.1 Generic Item / Block registration analyzer

### 必須辨識

- `GameRegistry.registerItem`
- `GameRegistry.registerBlock`
- `new Item...` / `new Block...` allocation site
- constructor args
- `setUnlocalizedName`
- `setTextureName`
- `setCreativeTab`
- stack size / durability / hardness / resistance / light / material 等可證明靜態屬性
- ItemBlock 關聯
- static field identity 與 register call 的資料流

### 新元件

- `LegacyRegistryAnalyzer`
- `LegacyContentDefinition`
- `GenericContentPass`

### 驗收

- 不含任何 `ruby/bamboo` 字串的 synthetic fixture 能得到同型定義。
- Bamboo corpus 能自動發現其 item / block registration，不靠人工清單。
- 未能證明的動態註冊只標 `MANUAL_REQUIRED`，不得猜 ID。

## P0.2 CoreMod activation + semantic intent analyzer

現行 `LegacyBytecodeAuditPass` 對任何 CoreMod marker 直接 `UNSUPPORTED`，過度保守。

### 要改成

1. 區分「JAR 中存在 transformer 類」與「實際透過 manifest / loading plugin 啟用」。
2. 解析 `IFMLLoadingPlugin.getASMTransformerClass()` 返回的 transformer 列表。
3. 解析 `IClassTransformer.transform` 的 target class / target method / 注入位置。
4. 可證明等價的 transformer 轉成 semantic requirement；未支援才 BLOCK。

### Bamboo 已知 intent

目前來源中 transformer 用途集中在舊附魔介面：

- `EnumEnchantmentType.canEnchantItem(Item)` 周邊的可附魔事件。
- `ItemHoe` 的 enchantability 行為。

這些應該變成 generic enchantability capability，而不是在 1.21.11 重跑 legacy ASM。

### 新元件

- `LegacyCoremodActivationAnalyzer`
- `LegacyTransformerIntentAnalyzer`
- `CoremodSemanticRequirement`

## P0.3 Generic Forge Event compiler

目前事件支援主要是 jump / fall / hurt。

Bamboo corpus 需要擴充常見事件族：

- crafted
- player tick
- living drops
- living death
- attack entity
- item tooltip
- player name format
- play sound at entity
- arrow nock / arrow loose
- render living specials pre（client-only）

### 規則

- 仍維持 register provenance proof。
- priority / receiveCanceled 等非預設設定沒有 adapter 時不得假裝 NORMAL。
- server-authoritative event 不在 client duplicated execution。

## P0.4 Recipe / OreDictionary / Fuel

### 支援

- `GameRegistry.addRecipe`
- `GameRegistry.addShapelessRecipe`
- `GameRegistry.addSmelting`
- `ShapedOreRecipe`
- `ShapelessOreRecipe`
- `OreDictionary.registerOre`
- `OreDictionary.getOres`
- `IFuelHandler`

### 現代輸出

- shaped / shapeless / smelting recipe JSON
- source-derived item tags / compatibility tags
- fuel registry/runtime adapter

---

# P1 — 世界內容核心

## P1.1 Block behavior compiler

新增受限 Block callback / property 語義：

- placement / break / activate
- neighbor update
- scheduled/random tick
- drops
- collision / selection shape
- metadata/state property
- facing / rotation
- growth / plant behavior
- light / material / opacity

禁止把無法證明的舊 metadata 任意映射成現代 BlockState。

## P1.2 TileEntity → BlockEntity

### 提取

- `GameRegistry.registerTileEntity`
- TileEntity class binding
- `readFromNBT` / `writeToNBT`
- update packet / description packet
- tick behavior
- inventory capability (`IInventory`, `ISidedInventory`)

### 新元件

- `LegacyBlockEntityAnalyzer`
- `LegacyBlockEntityBehaviorCompiler`
- `LegacyInventoryAdapter`
- `LegacyBlockEntitySyncAdapter`

## P1.3 Container / GUI

### 提取

- `NetworkRegistry.registerGuiHandler`
- `IGuiHandler`
- `EntityPlayer.openGui`
- Container / Slot / IInventory bindings

### 現代映射

- MenuType / AbstractContainerMenu
- Slot definitions
- client Screen binding

GUI 純 presentation 若無法自動搬移，不得阻塞 server-side inventory/menu 語義的獨立驗證。

## P1.4 EntityType + DataWatcher

### 提取

- `EntityRegistry.registerModEntity`
- `EntityRegistry.addSpawn`
- base entity kind / size / tracking / update frequency
- DataWatcher indices/types
- owner / motion / rotation / NBT

### 現代映射

- EntityType
- SynchedEntityData
- spawn/tracking bridge

---

# P2 — Render / Projectile / Special Armor

## P2.1 Entity renderer

處理可證明的：

- `RenderingRegistry.registerEntityRenderingHandler`
- ModelBase / ModelRenderer hierarchy
- texture binding
- bounded GL transform state

## P2.2 TESR / custom block renderer

- `TileEntitySpecialRenderer` → BlockEntityRenderer
- `ISimpleBlockRenderingHandler` / RenderBlocks / Tessellator 靜態幾何 → baked/model geometry
- 動態資料依賴 → BlockEntityRenderer

## P2.3 Projectile / bow / dispenser

在 alpha.25 已驗證 use/duration/release 基礎上擴充：

- projectile type
- owner
- velocity/gravity
- hit entity / hit block
- pickup
- dispenser behavior

## P2.4 `ISpecialArmor`

轉換：

- getProperties
- getArmorDisplay
- damageArmor

保持原 damage absorption 語義；不能簡化成普通 armor 值。

---

# P3 — Worldgen / Dimension / optional integrations

## P3.1 World generation

- `IWorldGenerator`
- biome predicates / BiomeDictionary type
- source worldgen trees / plants / patches

映射成現代 configured/placed feature 與 biome injection。

## P3.2 Dimension

- WorldProvider
- WorldChunkManager
- ChunkProvider
- DimensionManager registration
- teleporter

這一階段必須獨立於普通 overworld content；維度轉換失敗不能讓已證明可移植的普通 Item/Block 行為被誤稱成功。

## P3.3 Loot / grass / optional API

- ChestGenHooks → loot injection
- grass seed/drop hook
- NEI 類 integration：目標 API 不存在時安全移除，不阻塞主體
- CoFH Energy / SextiarySector：optional adapter；沒有對應現代能力時明確 diagnostic

## P3.4 Config

- 保留 source config value / default / bounds / category
- 舊 Forge config GUI 不作為核心 gameplay blocker
- 可另產生現代設定 UI adapter

---

# 測試策略

每一類能力至少三層：

1. **Synthetic fixture**：完全不同 namespace / class 名稱，證明不是 corpus hardcode。
2. **Bamboo corpus assertion**：對固定 SHA 驗證實際提取數量、關係與重要語義。
3. **Regression**：RPGTool 與既有 FML/translation/render tests 全部維持通過。

額外要求：

- 同一來源轉換兩次產物 byte-for-byte deterministic。
- 所有 analyzer 有 budget / recursion limit，避免惡意或異常 bytecode 導致無界分析。
- source class 永不初始化。
- unsupported method 只拒絕該 callback/definition，除非它是必要 registry identity 或安全關鍵行為。

# 交付順序

1. 完成 P0，讓 Bamboo 不再因「單純存在 CoreMod class」被粗暴 BLOCK，且能自動發現 generic content/recipes/events。
2. 完成 P1，讓主要世界內容、容器、TileEntity、Entity 能生成現代 registry/runtime 定義。
3. 完成 P2，補齊 presentation、投射物與特殊護甲。
4. 完成 P3，補 worldgen、dimension 與 optional integrations。
5. 完整 CI + Bamboo corpus deterministic conversion。
6. 產生 Bamboo 測試 JAR，交使用者實機驗收。

在第 6 步以前，不宣稱 BambooMod 完整轉換成功。