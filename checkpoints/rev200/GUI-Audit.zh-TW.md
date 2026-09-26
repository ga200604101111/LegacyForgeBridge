# Bamboo GUI：現況是模板相容，不是通用轉換

基準：LegacyForgeBridge `419d183e8fe31516b2e8152daabbb36a2584b457`。

## 查核結論

使用者的觀察正確。「不依賴 BambooMod 這個名字」不等於「可轉換不同布局、不同邏輯的 GUI」。目前營火功能是來源指紋通過後，套入既定介面家族；不是把原本任意 GuiContainer／Container 程式翻譯成現代介面。

直接證據來自 rev194 checkpoint 的完整 `LegacyRemoteMenuAnalyzer` 類別，以及目前分支保留的 `checkpoints/rev197/Generality-Audit.zh-TW.md`。這次讀取並驗證 rev194 的第一個 Git blob，從它完整可解出的 patch 前段讀到該類別；沒有宣稱已還原整條 rev189–199 原始碼鏈，或讀完全部 GUI runtime。

| 層次 | 查到的實作限制 |
|---|---|
| GUI 註冊 | 只接受特定 FML lifecycle 內、直接 new 的 IGuiHandler 註冊形狀。 |
| GUI 分流 | 依賴指定參數槽位的 switch；每一分支必須符合 12 條有效指令的固定結構。等價 helper、其他條件分流未涵蓋。 |
| 繼承 | Container 與 GuiContainer 要直接繼承指定原版類別。 |
| 核心辨識 | 容器建構式、GUI 建構式、property 更新、Shift-click、背景繪製，合計五個固定 SHA-256 指紋。 |
| property | 來源寫入欄位必須恰好兩個，且與 GUI 讀取順序一致。 |
| 貼圖 | 特定靜態初始化中的唯一字面 ResourceLocation，且圖片必須是 256×256。 |
| 輸出契約 | Proof 只有 GUI ID、登錄名、block/tile/container/gui 類別與 texture；沒有槽位布局、完整 property 映射或繪製運算。 |
| 執行時家族 | rev197 審查明確記載 47 格、兩個 property 的營火家族布局。這一列採用該審查紀錄，並非本次重新實測 runtime。 |

因此，即使改掉類別與模組名稱仍能辨識，也只能說「名字無關的模板辨識」；不能作為其他布局、其他機器或其他模組已支援的證據。

## 不能採用的假泛化

不要只移除指紋、放寬槽位／property 數量，卻仍送入固定營火畫面。那樣可能造成畫面槽位和 wire slot ID 不同、點擊錯位、Shift-click 錯誤、同步欄位解讀錯誤，甚至客戶端看似成功但被伺服器否決。

這次沒有刪除既有安全檢查、沒有重寫營火布局，也沒有將未知 GUI 靜默套入營火模板。Bamboo GUI 的通用化尚未完成。

## 真正需要的改造

第一層是來源契約：從註冊、伺服器 Container 與客戶端 GUI，抽出同一份可驗證的 MenuContract。至少包括依加入順序排列的 wire slot、來源 inventory/index、x/y、可放入／可取出的條件、property ID 與欄位對應，以及畫布尺寸、貼圖實際尺寸、UV、文字與進度條運算。數值來自來源，不是固定 47／2／256。

第二層是共用執行器：現代 Menu／Screen 消費 MenuContract，不依賴「這是營火」的分支。可先限定純貼圖、文字、Slot 與可驗證整數進度運算；複雜自訂繪製或封包不在已證明範圍內時，輸出明確拒絕原因。

第三層是遠端協定：GUI ID、window ID、slot ID、property 更新、點擊／Shift-click／拖曳、close 和交易確認保持伺服器權威。不得要求修改 1.7.10 伺服器，不在客戶端憑空產出合成結果。

既有營火模板在新路徑真正通過回歸前保留；後續將其標示為 legacy template fallback，而不是宣稱任意 GUI 都可用。

## 驗收條件

必須讓不同槽位數、不同座標、不同 property 數量、不同 GUI ID、不同貼圖尺寸的獨立來源，產出相應而非相同的契約與畫面；不能只做改名測試。還要比對原 1.7.10 的槽位順序、滑鼠點擊、Shift-click、拖曳、進度更新與關閉行為。拒絕案例要證明不會被偷偷塞進營火模板。

以上是查核結論與實作邊界，不是已完成通用 GUI 引擎的聲明。
