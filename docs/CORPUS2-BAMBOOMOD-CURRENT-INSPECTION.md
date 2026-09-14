# Corpus #2 — BambooMod 當前候選檔實檔檢查（2026-09-15）

本文件記錄使用者實際產生的 `Bamboo-2.6.8.5-lfb.jar` 狀態，用來作為下一輪 conversion 工作的 factual baseline。不要把本文件當成新建置或遊戲實測報告。

## 結論

目前候選檔 **有部分轉換內容，但尚未完成可安裝、可遊玩的 Fabric port**。

使用者日誌：

```text
Conversion result for Bamboo-2.6.8.5.jar: status=PARTIAL, profile=generic-forge-1.7.10, loaderSafe=false, candidate=Bamboo-2.6.8.5-lfb.jar, managed=none, phase=PARTIAL
```

Candidate manifest 亦為：

```text
status=partial
installable=false
```

## 檔案識別

Original：

```text
Bamboo-2.6.8.5.jar
size = 1,319,593 bytes
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

使用者產生 candidate：

```text
Bamboo-2.6.8.5-lfb.jar
size = 1,469,529 bytes
SHA-256 = 82327a139d570496992b22c587d024cd8b02324dec147c09997083c2b15aa338
```

Converter：`0.2.0-alpha.26` / `generic-forge-1.7.10`。

ZIP integrity 已檢查通過。

## Candidate 中實際存在的轉換輸出

| Surface | 實際輸出 |
|---|---:|
| Generic Item identity/skeleton | 42 |
| Generic Block identity/skeleton | 63 |
| Recipe JSON | 125 written / 37 skipped |
| Placement rules | 12 written / 10 skipped |
| Activation callbacks | 19 source callbacks |
| Activation pure rules | 3 |
| Activation effect rules | 3 |
| Activation remaining skipped | 13 |
| Fuel rules | 2 / 0 skipped |
| Lifecycle analysis records | 42 |
| Generated classes | 48 |
| Original legacy classes still present | 341 |
| Total classes in candidate | 389 |

注意：Item/Block registration 數量只證明 identity/skeleton，不等於 42/63 個內容都已完整可玩。

## `4ee9da1` 新增的灰泥效果確實存在

以下三個 generated block rule 已在 candidate 中：

- `bamboomod:decoplaster`
- `bamboomod:decocurveplaster`
- `bamboomod:decodcurveplaster`

共同條件：

```text
held item = bamboomod:itembamboo
legacy notification flag = 2
finite table = 1152 input tuples / rule
```

這證明 compiler/materializer 已輸出規則，不證明 Fabric 已真正載入 candidate，也不證明聯機旋轉同步已實測。

## `loaderSafe=false` 的直接證據

Original 341 個 source class 全部仍存在於 candidate，且檢查時全部與 original JAR 中對應 class bytes 相同。

因此 candidate 仍包含大量 Minecraft 1.7.10 / Forge/FML executable bytecode。

目前 installer policy 不把這種 candidate 視為安全成品；轉換報告亦有：

```text
LFB-CONVERT-BYTECODE-0001
Original legacy class files remain in the candidate (341; generated modern wrappers=48).
The generated JAR is not yet a completed Fabric port.
```

不要透過關掉這個檢查、把 boolean 改成 true，或直接刪除 341 class 來宣稱完成。需要先證明哪些 class 已被替代／不可達，以及必要 semantics 是否已遷移。

## 主要未完成能力群組

### 1. Source item/class constructor/API families

目前診斷仍包含多種未支援 API，例如：

- `ItemFood`
- `ItemSeeds`
- `ItemSeedFood`
- `ItemReed`
- `ItemSnowball`
- `ItemBed`
- projectile `EntityThrowable` / `EntityArrow`
- old `IIcon`
- `WeightedRandomChestContent`
- direct `InventoryPlayer` arrays
- player/entity fields and old potion/static registries

這些應按 shared adapter/family 解決，不按 Bamboo item 名稱硬編碼。

### 2. Forge/FML event runtime adapters

Source-proven 但尚無完整 runtime adapter 的事件包括：

- `ItemCraftedEvent`
- `LivingDropsEvent`
- `NameFormat`
- `PlayerTickEvent`
- `AttackEntityEvent`
- `LivingDeathEvent`
- Bamboo 自訂 enchantability events

其中 server-authoritative event 不應在 client 重跑 gameplay effect。

### 3. Block interaction / BlockEntity / Inventory / Menu

部分 activation 依賴：

- TileEntity state
- inventory slot state
- hit coordinates
- current held stack
- creative-mode state
- item decrement/drop
- world/block update
- GUI opening

例如這類 callback 不能只保留 `return true`。需要完整 modern BlockEntity/Menu/sync 或明確 remote-server authority path。

### 4. Rendering

Candidate 還有：

```text
LFB-CONVERT-RENDER-0001
Direct legacy OpenGL references remain in staged source class files and require semantic rendering migration.
```

還需要追實際 renderer registration 與必要 presentation semantics。不能用 generic cube 或直接省略 renderer 宣稱完整。

### 5. Forge/FML bytecode references

Candidate 還有：

```text
LFB-CONVERT-FORGE-0001
Forge/FML bytecode references remain in staged source class files.
A conversion pass or runtime adapter must replace them before installation.
```

這與 341 source class 未 finalization 有關，但仍需按 dependency/call graph 確定哪些是必要、哪些可安全排除。

## 已知目前不是問題的部分

- Bamboo original SHA 已拿到，現在不需要再用公開 source 冒充 exact corpus。
- 三個灰泥 held-item metadata effect 已正式進 branch `4ee9da1`，且 CI #212 全綠。
- Dormant CoreMod marker 不是目前整包 loaderSafe=false 的主要 blocker；source manifest 未啟用 FMLCorePlugin。
- `status=PARTIAL` 是目前合理結果，不是需要修改的文字 bug。

## 下一輪調查輸出應至少包含

### Class dependency classification

對 341 source classes 建立：

```text
class
source role
runtime reachable?
client/server side
references from generated code/resources?
modern replacement?
remaining semantics?
action = exclude / convert / adapter / unresolved
proof
```

### Capability dependency graph

按 capability 分組所有 diagnostics：

```text
BlockEntity / Inventory / Menu
ItemFood/Seeds/Reed families
Projectile entities
Block callbacks
Renderer/OpenGL
Events
Recipes/Ore/meta
Entity tracking/DataWatcher
Worldgen/dimension
```

用「解除多少 necessary source dependencies」決定優先度，而不是只依 warning 數量。

## Loader-safe candidate 的驗收標準

Candidate 要進入可安裝階段至少需要：

1. 不再把未證明安全的 original legacy executable classes暴露給 Fabric loader。
2. Generated bytecode 不殘留無法解析的 1.7 Forge/Minecraft superclass/descriptor/calls。
3. Necessary source semantics 已 materialize 或被 modern runtime adapter 接住。
4. `ManagedCandidateInstaller` 可以依正常 policy 接管，不靠 bypass。
5. Restart 後能真正載入 candidate。
6. Exact corpus conversion 可 deterministic 重現。
7. Gameplay smoke test 通過，而非只進主選單。

## 最小實機 smoke test

當 loader-safe 門檻達成後，至少測：

- 啟動無 class linkage / Mixin failure。
- 連入目標 Forge 1.7.10 server。
- Bamboo content identity 正確。
- 必要 texture/model 不遺失。
- 三個灰泥：放置、正確 item 旋轉、錯誤 item 不旋轉、server correction、重連一致。
- 第一個完整容器：slot click/shift-click 無 duplicate/loss、同步正常。
- normal restart 後 managed candidate 正常載入。

## 目前判定

```text
LFB build: PASS for commit 4ee9da1 / CI #212
Activation-effect compiler: PASS for tested generic fixtures
Bamboo effect materialization: present in actual candidate
Bamboo loader safety: FAIL / not yet satisfied
Bamboo managed installation: NOT REACHED
Bamboo gameplay validation: NOT YET VERIFIED
Overall Bamboo conversion: PARTIAL
```
