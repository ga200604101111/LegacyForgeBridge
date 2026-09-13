# alpha.25 工作暫存／下次接續

狀態：依使用者要求暫停，只保存目前修改。這是 WIP 原始碼快照，不是已通過建置或可安裝的發行版。

分支：`feature/runtime-content-conversion-rpgtool`。
本輪修改的基底：alpha.24 commit `5afad01bfdd803726a6705783aca5ef712208cd3`。
暫停前已整理的 Git tree：`49a0d2c789e2b61cdac090647674c14051316f79`。
本次存檔使用 `[skip ci]`；不合併 main，不發布 JAR，不安排自動續做。

## 已保存的工作範圍

- 通用 Item 建構分析與 source-compiled behavior compiler／adapter：保留可支援的 tooltip、NBT 條件、使用動作、使用時間及事件回呼，而非替 RPGTool 逐項硬編功能。
- ConvertedBehaviorItem、現代開始／結束使用流程，以及 Via 最終物品還原邊界的使用元件補接草稿。
- 原生 tooltip 接點與舊格式文字處理；從目前 ItemStack 的 NBT 讀取動態資料。
- 裝備／事件 adapter 草稿：跳躍、摔落、傷害和物品 tick 等已辨識回呼。
- 手持座標換算、左右手與第三人稱格擋模型分支草稿；保留既有 OBJ 幾何／UV 與穿戴動畫修正。
- alchemy／astronomy 獨立 fixture 與相關回歸測試；版本欄位已預備為 alpha.25，但尚未發行。

## 下次必須先完成

1. 檢查這個快照的整合接線與 1.21.11／Via API 相容性，執行完整 Gradle 建置和測試；目前沒有本輪 CI 成功的結論。
2. 核對轉換 pipeline、產生的 bootstrap、Mixin 與客戶端入口，再檢查正式 remap JAR、命名空間及 installer／cache 回歸。
3. 對照原始 JAR 驗證 tooltip、一般劍擋與 NBT 特殊使用動作，以及裝備效果的客戶端／伺服器責任，避免效果重複執行。
4. 通過上述檢查後才交付測試版。握持位置、格擋放開封包、實際畫面與伺服器行為仍需要 Minecraft 實機驗證。

## 重要背景與界線

原始 RPGTool 的 SwordBase 確實覆寫右鍵、使用動作和停止使用；前面「完全只是繼承 ItemSword」的說法不正確。原程式依 NBT 切換格擋／蓄力，不能把所有劍無條件強制成同一種格擋。

本輪有在本地以 JDK internal ASM 作替代進行 prototype／生成回呼的 JVM 檢查，包含獨立 namespace fixture 與原始資料的部分行為測試。這不等於使用正式相依套件完成 Fabric 建置，也不是 GPU 或實機封包驗收。

不支援的原始方法應保留診斷；不要把這個快照稱為 RPGTool 或任意 Forge 模組的完整移植。舊伺服器仍執行的功能不可在客戶端再重複套用。

原始測試輸入：`RPGTool1-1.1-1.7.10.jar`；對話中的掛載附件名曾為 `2be22db9-557c-4c77-bb5d-ae89d1895b70.jar`。
SHA-256：`b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d`。
來源 JAR 未在這次存檔中加入 repository；需要時從使用者原始附件／old-mods 取得，不要用已刪除舊 class 的 converted JAR 代替。

維持使用者既有要求：通用轉換優先、輸出 `原檔名-lfb.jar`、不憑空補 zh_TW、發布命名空間 yinghuang。使用者下次明確要求繼續時，再從此快照接續。
