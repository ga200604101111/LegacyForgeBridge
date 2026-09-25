# rev197 通用性審查與 Bamboo 身分流程

## 結論

**目前不是全部通用。** 不依賴模組名字、辨識同類程式形狀、任意模組相容，是三個不同層次。來源審查以 rev196 提交 0f7db002899d112017742c62a60a6a753ee807f3、累積來源 checkpoint 與實際發布 JAR 為基線；包含來源樹全域名稱／指紋／入口搜尋及關鍵流程閱讀，不是所有方法的完整語義證明。

## 食譜斷點與修正

使用者 log 在 00:04:19、00:04:42 記錄欄位 5、4 的 cookingrecipe 請求及回傳書本開啟。舊 useItem 只認 RETURNED_WRITTEN_BOOK 的來源道具；伺服器替換成真正的 minecraft:written_book 後，後續使用已不符合來源家族而 PASS。舊 JAR 負對照重現首次有開書呼叫、第二次沒有。

rev197 在 1.7.10 目標連線中，每次明確使用實際已完成書本都呼叫原生閱讀處理器。成功後 Fabric FAIL 用於停止額外處理／封包，不是向玩家顯示失敗。來源道具仍透過 SUCCESS 發送普通使用，等待真正伺服器替換；不改物品、數量、作者、標題，不記錄欄位白名單。既有共用轉換模組初始化入口安裝此處理；不宣稱無任何轉換模組時也是獨立書本功能。現代目標、副手、旁觀者、錯誤世界、已有介面與空白普通書保持原流程。

## 分類審查

| 機制 | 實際通用程度 |
|---|---|
| FML 身分／網路版本 | rev197 共用靜態 @Mod、mcmod.info、version.properties 解析，不認 Bamboo 名稱；不等於 DummyModContainer 或其他模組載入機制已支援。 |
| 數字 Block／Item ID、metadata | 當次 ModIdData 驅動，区分 Block／Item，非固定 185／4242。新加入重複 wire key、重複數字／多對一映射拒絕。 |
| 登錄別名 | 精確來源身分優先，再用原有正規化；歧義拒絕，不能以載入順序選擇。拒絕衝突不是自動解決衝突。 |
| 現有書本再閱讀 | 指定 1.7.10 連線的共用行為，不依賴 Bamboo 或食譜標題。 |
| 來源回傳書本 | 已移除 book_return 固定指紋。新有界直線資料流辨識建立 written_book、附 NBT、回傳同一書本；支援 3 種建構式、helper 名稱／靜態與實例變化、局部變數及繼承。18 種獨立實作有測試，未知分支／例外路徑仍拒絕。只辨識回傳協定，不證明或執行 NBT helper。 |
| 供盤 | TRAY_ITEMS 家族仍有 5 個 watcher、模型／數值條件等限制。原版物品遷移、底部對齊與縮放修正可於家族內共用，不是任意舊 GL 渲染器。 |
| 花盆 | 世界模型／原版資料遷移可共用；GridPot 的槽位、拓樸及幾何證明仍有限。optional insertion predicate 正負集合衝突未修。 |
| 風鈴 | 註冊／實體 ID／貼圖來自來源，但懸掛模型仍依賴 renderer、update、model、animation 指紋與兩段幾何條件。不是通用實體渲染翻譯。 |
| 營火 GUI | LegacyRemoteMenuAnalyzer 仍含固定指紋及 47 格／2 個 property 家族布局。原生同步基礎可共用，任意 IGuiHandler／任意介面尚未支援。 |
| 蒸氣與 VillagerBlock | auxiliary-instruction-shapes 其餘角色指紋仍在。模型／粒子提交共用，但幾何、動畫、隨機條件與交易／儀式／佩戴語義並未全面泛化。 |
| 初始區塊補建 | 針對橋接器擁有 EntityBlock、舊版連線、區塊載入的共用修正，不是任意第三方方塊修復器。 |
| Property／時鐘 ABI | 1.21.11 指定 API 修正，非 Bamboo 特例，也不保證其他 Minecraft 版本。 |
| 歷史 RPGTool1 profile | 程式庫仍有 RpgTool1Profile 與 3 個專用 pass，含名称／資源路徑／corpus SHA。不能說整個程式庫沒有專用碼；標準 LegacyConversionManager → LegacyConversionEngine(analyzer) 目前只預設 GenericLegacyModProfile，不走該歷史 profile。 |

沒有為了讓未知程式被接受，就刪掉風鈴或營火的檢查而送進固定實作；那只會擴大錯誤。其餘模板尚未全面參數化。水面／流體、交易、CoreMod、自訂封包與全部家具遊戲語義仍不完整。

## 模組 ID、名稱與版本怎樣用於加入伺服器

1. 掃描 old-mods 的原始 JAR，使用 source SHA 與 converter fingerprint 管理更新／快取。檔名僅用於檔案定位與展示，不當成 FML 身分證據。
2. ASM 靜態讀取 cpw.mods.fml.common.Mod 的 annotation。保留 modid 精確大小寫；展示名稱與展示版本取自 mcmod.info，缺少時以 annotation 等資料補足。缺 metadata 的真實 annotation 模組不再改用檔名猜 ID。
3. 網路版本順序依據官方 MinecraftForge/FML 分支 1.7.10 的 FMLModContainer.bindMetadata：@Mod.version → version.properties 的 <modid>.version → mcmod.info.version → 1.0。非空 placeholder 也是實際 wire 版本，不從檔名猜測修正。
4. GenericContentPass 寫入 converted-content.json 的 legacyMods，分開展示版本與 networkVersion；沒有 GameRegistry 内容也保留實際 annotation。純 metadata／檔名資訊不再自動宣告為已安裝 FML 模組。
5. ConvertedModCatalog 只收集已載入且非 stale 的轉換候選。重複 ID、FML／Forge／mcp 保留身分與別名衝突拒絕。多 annotation 網路名單有測試，但完整多邏輯模組內容命名空間／生命週期仍不全面支援。
6. FmlHandshakeClient 經 ViaFabricPlus 發送 ClientHello／ModList，使用來源 modid 與 networkVersion，不傳展示名稱，也不抄伺服器 ModList 假裝安裝未知模組。現有框架宣告固定 mcp 9.05、FML 7.10.99.99、Forge 10.13.4.1614，代表相容層基線，不代表完整 Forge 已運行或所有小版本皆相容。
7. 伺服器 ModIdData 回傳本次數字 ID，透過來源登錄名稱／manifest 別名對映到現代 Block／Item 與 metadata，最後完成 Ack。COMPLETE 只表示握手完成，不等於任意模組所有行為完整。

### 原始 Bamboo 樣本與這次 log

| 欄位 | 實際值 |
|---|---|
| 原始檔名 | Bamboo-2.6.8.5.jar，不是網路 ID |
| 展示名稱 | BambooMod |
| 展示版本 | Minecraft1.7.10 ver2.6.8.5 |
| FML ID | BambooMod |
| Fabric ID／主要 namespace | bamboomod |
| 網路版本 | Minecraft@MC_VERSION@ var@VERSION@ |
| 原始食譜登錄身分 | BambooMod:cookingrecipe |
| 現代食譜身分 | bamboomod:cookingrecipe |
| 使用者本次連線的數字 ID | 4242，伺服器提供，非固定值 |

網路版本與使用者 Client／Server ModList 相同。這次修正未將它擅自替換成 2.6.8.5。

## 驗證邊界

本地針對性重建、發行 JAR 的 codec 純 JVM 測試、合成來源靜態分析、API／宿主替身回歸與舊版負對照完成；17 個選定轉換 pass 仍為 PARTIAL。没有完整 Gradle／Loom、實際 Minecraft／Fabric／Mixin 或真實伺服器反覆開書驗收。詳見 verification.json；下載的來源建構 ZIP 另附較詳細審查報告、完整本地 builder 與測試紀錄。
