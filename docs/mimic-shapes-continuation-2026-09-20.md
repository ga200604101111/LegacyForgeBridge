# 模仿方塊、薄片與破圖：2026-09-20 續作修補

## 適用基線與交付狀態

- Repository: `ga200604101111/LegacyForgeBridge`
- Branch: `feature/generic-conversion-bamboo-corpus2`
- Base commit: `fd75cadf77bd25ab7f46ff6fb887412921737f18`
- Base CI: successful workflow run `35462722315`; unmodified version `0.2.0-alpha.27`.
- Converter revision: `2026-09-20.141` → `2026-09-20.142-mimic-shapes`.
- 外部測試來源：使用者提供的 BambooMod Minecraft 1.7.10 v2.6.8.5。
- Source SHA-256: `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.

本交付是針對該 commit 的**原始碼修補**，不是完整 checkout，也不是可安裝的 mod JAR。本修補將由 GitHub Actions 執行完整 Gradle/JUnit 建置；遊戲內驗收仍需實機確認。不要將來源 Bamboo JAR 或測試依賴重新包進橋接器。

## 修改內容

### 1. 模仿方塊：把材質證據與形狀證據分開

原本世界圖示解析失敗時，即使台階形狀已經由來源證實，也會排除該 metadata，最終可能使用舊模型。現在僅對另外通過原始 render type、來源綁定 renderer 與相鄰方塊投影證據的 mimic family，允許以明確標記保留形狀並採用已解析的物品材質。

新增每狀態 `materialMode: "inventory_fallback"` 與非空 `materialFallbackReason`；不得捏造模仿方向。一般 renderer 不會因此被一律認定成方塊。幾何解析器會拒絕未知模式、空理由、非 mimic 降級或降級又聲稱已證實來源方向的資料。報告新增 `inventoryMaterialFallbackVariants`。

這份 corpus 的 `delude_width`、`delude_height` 各在 metadata 6、7、14、15 使用上述降級，共 **8 個狀態**。它們不是完整動態模仿成功的宣告；部分直立類型的 bounds 在來源本身就是整塊，不應擅自改成半磚。

世界材質查找不再把「帶有水流體的實體方塊」一律當成水，改為略過真正的空氣／水方塊。模仿目標是含水階梯、含水台階等時，不再因 waterlogged 而被錯誤排除。模型本身仍使用模仿方塊的幾何，不複製目標整塊形狀。此客戶端改動尚待完整 Minecraft 編譯及實測。

### 2. 薄片竹籬笆、簾幕：統一世界與物品／備用模型

這份 corpus 的 `bambooPanel` 繼承 `BlockPane`，不是原版 `BlockFence`。保留來源可證實的 0–6 共 7 個變種，不捏造 7–15 的模型。使用共同 `paneFaces(mask, edges)`：有邊框採薄片表面，無邊框採雙面零厚度簾幕。

修正簾幕 metadata 4、5 的生成世界／手持模型仍使用厚玻璃片的問題；加入連接遮罩範圍驗證。既有竹籬笆連接規則保留，並非宣稱所有 mod 的任意柵欄都已修好。

### 3. 繼承的薄型方塊

辨識來源繼承的 `BlockPressurePlate` 與 `BlockCarpet` 窄範圍呈現語意：

- 壓力板材質來自原始建構子，正常厚度 1/16，metadata 1 壓下厚度 1/32，保留其原始物品 bounds。
- 地毯高度 1/16；允許來源自己的 `getIcon` 將六個面委派給可證實的已註冊方塊，不虛構材質。
- 保留來源覆寫的檢查和碰撞解析限制，不合成紅石／壓力板 gameplay。
- 裝飾半磚 `halfDeco` 保留 upper-half bit；榻榻米 `halfDirSquare` 依來源固定下半部，不錯把 meta 8 改成上半磚。

### 4. 快取與來源素材

提高 converter revision 以使修補後的轉換能區別舊快取。所有原始 PNG／動畫資料原封不動保留；不以重畫貼圖掩飾幾何錯誤，也未把輸入模組 class 載入執行。

## 已實際執行的驗證

使用 Java 21，將修改過的轉換／幾何／schema/pass 類別編譯後置於原 CI JAR 前方，執行九個真實 presentation passes。其餘實作使用原 CI JAR。由於本環境無 Gradle 與 Minecraft runtime 依賴，本機獨立測試使用既有套件內的 ASM/Gson，並僅在測試依賴補上所需便利 API；這些測試依賴未包含於交付檔，也不代表正式依賴編譯已通過。

已以補丁內的 `MimicShapeRegressionChecks.main` 及 `MimicShapeExactCorpusChecks.main` 重新執行，並用 Python 檢查生成輸出。JUnit 包裝測試已提供，但沒有聲稱在這裡執行 JUnit runner。

| 項目 | 結果 |
|---|---:|
| 幾何規則 | 19 → 21 |
| metadata 狀態 | 287 → 327 |
| 原有狀態逐項保持相同幾何 spec | 287 |
| 明確標記的物品材質降級 | 8 |
| 檢查的世界／物品模型檔 | 654 |
| 檢查的 owned texture references | 4,578 |
| 缺少的 owned texture references | 0 |
| 原始 PNG／動畫 entries 位元組相同 | 223 |
| 階梯形狀組合 | 256 |
| 薄片／簾幕 mesh 組合 | 32 |
| 執行的 presentation passes | 9 |

Minecraft 自帶貼圖引用不計入 owned texture 檢查；不能將零缺失解讀為遊戲內所有方塊已無破圖。

九個 passes 依序為：CopyLegacyJar、LegacyLanguage、GenericContent、LegacyClientContentBaseline、LegacyIconPresentation、LegacyItemName、LegacyCreativeVariants、LegacyBlockGeometry、LegacyTextureAtlas。這是呈現管線子集，**不是完整 LegacyConversionEngine 的端到端驗證**。完整引擎在本環境因缺少 Minecraft registry runtime `net/minecraft/class_2378` 而無法完成。

## 套用與正式建置

先保存目前未提交的修改。在真正的完整 repository checkout 內執行，而不是此增量 ZIP 內：

```powershell
git switch feature/generic-conversion-bamboo-corpus2
git apply --check .\LegacyForgeBridge-mimic-shapes-fd75cad.patch
git apply .\LegacyForgeBridge-mimic-shapes-fd75cad.patch
.\gradlew.bat test build
.\gradlew.bat exactCorpusTest "-PlfbExactCorpusJar=C:\path\to\Bamboo.jar"
```

若 `git apply --check` 報告衝突，先核對基線 commit 與本地修改；不要強制覆蓋。上面的測試／建置命令是待於正式環境執行的步驟，不是本次已完成的結果。新增 exact-corpus 測試需上述 SHA-256 的檔案。

`tools/geometry/verify_bamboo_geometry_continuation.py` 可比對同一輸入的基線／修補後 presentation 輸出目錄並重建驗證 JSON。

## 尚待遊戲驗收

重新建置橋接器並重新轉換該來源，再檢查：竹籬笆 0–6 的單獨及相鄰擺放、簾幕雙面與手持模型、模仿台階上下半與方向、模仿階梯上下／轉角、含水方塊作材質來源、壓力板下方材質、地毯厚度，以及世界／背包切換後的材質一致性。

相鄰動態模型、完整自訂 renderer 等價性、其他來源模組的 BlockFence、自訂碰撞與紅石／互動玩法均不包含於本輪通過宣告。八個降級狀態的動態材質仍需後續證據才能實作完整等價轉換。
