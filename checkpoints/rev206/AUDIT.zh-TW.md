# 投射物通用性審查：rev205 實際瓶頸與 rev206 報告修正

## 結論

目前是有限家族的「遠端投射物呈現相容」，不是任意舊版投射物的通用轉換。物品圖示、武器持握與右鍵使用程式完成，不能推論飛行中的實體已完成。這次只修正拒絕原因遺失，沒有交付新的投射物修復主 JAR。

## 一、最新日誌支持什麼

日誌版本是 rev205，轉換指紋正確。啟動時列出的投射物規則及渲染器是 Bamboo 的 2 條，沒有 iYAMATO 的相應載入紀錄。檔案有 21 筆 iYAMATO EntitySpawnMessage，全部是 modEntityTypeId=4803 且標記「header decoded; no admitted converted entity mapping」。其中測試脈絡為大馬士革匕首。這代表此路徑沒有把伺服器生成的實體建立為客端可用的轉換實體，不能只修改物品貼圖。

這份日誌不涵蓋所有投射物實機行為。另有一次 recentUse=none 的 timeout，不能歸因於射擊。Spear 的另一個阻擋仍存在：Bamboo 來源事件監聽尚未證明，ArrowNockEvent policy 為 allowed=false。不得為了讓蓄力動起來就把事件取消判斷一律改成 false。

## 二、來源和實際分析器的結果

原始樣本 SHA-256：35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e。

| 層級 | 實際結果 |
|---|---|
| 來源註冊記錄 | 54 筆實體，不是 54 種武器 |
| 身分／DataWatcher 分析 | 54 筆全因 registerModEntity 參數未完全證明而拒絕 |
| 具投射物基底或介面的候選 | 24 筆：10 Arrow、5 Throwable、9 其他基底加標記介面 |
| 有來源自訂 tick | 上述候選中 19 筆 |
| 綁原版 RenderSnowball | 上述候選中 5 筆 |
| rev205 投射物分析輸出 | 0 規則、0 排除、0 診斷；上游排除被漏掉 |
| 本次報告修正後 | 0 規則、24 排除、1 個上游總結；正式 pass 留下 24 個警告 |

來源以設定欄位初始化靜態計數器，再遞增註冊；部分後續註冊受設定條件控制。實體 ID 設定的來源預設為 4800，但它不是所有伺服器的固定身分。日誌的 4803 不能硬寫成通用的大馬士革匕首 ID，也不能拿列表下標取代網路 ID。註冊順序、設定來源、條件與計數器副作用都必須保留為可驗證證據。

24 個候選包含契約文件、隱形攻擊載體等，並非全是可見的武器彈體。完整清單見 entity-inventory.tsv；清單按繼承與介面辨識，不靠類別名稱含 Bullet、Arrow 或 Dagger。

## 三、目前的非通用限制

1. `LegacyProjectilePresentationAnalyzer.directUseItem` 要求唯一註冊物品在自己的右鍵方法內同時出現 NEW 和 World.spawn。它沒有完整追蹤同一個配置實體流入 spawn；也沒有涵蓋鬆開右鍵、持續使用、輔助方法、彈藥分支或多件物品共用實體。
2. 分析器把 EntityThrowable 與物品 billboard 綁在一起，把 EntityArrow 與定向畫法綁在一起；還要求渲染器必須存在於來源 JAR。原版 RenderSnowball 本身就不在來源 JAR，並不是無效渲染器。
3. 現有定向渲染驗證只檢查若干旋轉、縮放與固定貼圖特徵，不等於證明完整網格、UV、軸向和變換一致。執行階段目前委派給新版原版箭網格，不能據此宣稱重現每個來源自訂渲染器。
4. `ConvertedLegacyRemoteProjectile` 是基本 Entity 載體，沒有覆寫 tick 重播來源的投射物更新。只補生成身分，仍不足以保證軌跡、旋轉、碰撞呈現、粒子與消失時機正確。
5. 額外生成資料目前採整體拒絕；射手、速度、DataWatcher、讀寫欄位順序必須依各來源契約解析，不能把剩餘 bytes 忽略後稱為完整支援。

## 四、不能把所有「看不到」改成可見子彈

檢查來源 `ClientProxy.registerRenderThings` 的綁定，普通子彈、霰彈及 extended-reach 載體都使用 `RenderSnowball(ItemRegister.invisible_entity_projectile)`。`ItemRegister.init` 將該物品貼圖設為 `iymts_mod:invisible_entity_projectile`。實際 PNG 為 16×16，256 個像素 alpha 都是 0；不是單憑 invisible 這個名稱猜測。

因此這三者的物品精靈本來就透明，不應一律替換成可見箭。這不代表實體同步、粒子、命中及生命週期可以省略，也不能用它解釋本來有自訂可見渲染的飛刀／弩箭。魔法子彈綁另一個物品，不能跟普通子彈合併。驗證數據見 transparent-sprite.json。

## 五、通用修復的必要分層

- **來源身分契約**：保存 mod/entity 註冊關聯、設定值和遞增表達式，釐清伺服器與客端設定一致性；未解析不能替入日誌觀測 ID。
- **生成／狀態契約**：分開網路實體 ID、模組實體類型 ID、射手 ID；解析 typed DataWatcher、初速度與額外生成資料，驗證完整消費及安全邊界。
- **呈現契約**：獨立辨識原版 billboard、來源 Tessellator/模型程式、貼圖和 UV、旋轉縮放及透明特性，不能用父類或發射武器決定畫法。
- **客端生命週期**：只轉換來源在遠端世界執行的必要更新，接入生成、移動、速度、狀態、銷毀及換世界清除；原伺服器仍負責傷害、耗彈、爆炸和權威結果。
- **驗收**：更名、變更設定基值、共享發射器、release/helper 生成、透明及可見彈體、可變 metadata、亂序與缺包、落地及銷毀。分析成功、建立載體、畫面正確與玩法正確分別記錄，不能只報一個 runtimeComplete=true。

## 六、本次交付邊界

來源報告修正已通過 17 組合成 fixture、86 個分析器斷言、9 個實際產出 pass 斷言，並由 Python 獨立讀回排除 JSON。候選分類與排除不以 iYAMATO 名稱分流。這些是本地來源／JSON 驗證，不是遊戲測試。

沒有增加任何投射物執行規則、沒有交付 rev206 安裝包、沒有改跳躍、沒有更動伺服器或來源模組。rev202–205 舊來源的 819 檔校驗與解包也已完成；舊版測試數字沒有混算成本次結果。
