# rev201 — 客戶端來源槽位／同步欄位契約

基準：`1b266638e25da4986df306a9fa495ae8ffbd55cd`（rev200），分支 `feature/generic-conversion-iyamato-corpus3`。

**原始碼版本，沒有可安裝 JAR。** 本次實作已接線到來源分析、轉換 manifest、規則讀取、FML 選單開啟與客戶端選單。未完成完整 Gradle/Loom、Minecraft/Fabric/Mixin 建置與連服驗收。伺服器、原始 RPGTool/Bamboo、old-mods、main 與 Bamboo 分支不改。

## 實際變更

新增有界非執行式 `LegacyMenuTopologyAnalyzer` 與 immutable `LegacyMenuTopology`。從來源建構式抽取 Slot 加入次序、玩家／機器 inventory 身分、inventory index、x/y 及簡單放入限制，支援整數運算、局部變數、有限迴圈與封閉 helper。property ingress 逐一涵蓋 65,536 個 signed-short ID，傳入值保留符號；只接受直接寫入同一機器 int 欄位，拒絕動態／歧義／多重映射。

`LegacyRemoteMenuAnalyzer` 的新路徑用語義抽取取代建構式與 property setter 的兩個固定指紋。`LegacyRemoteMenuPass` 寫 schema 2 契約；`LegacyRemoteMenuRules` 驗證；`LegacyRemoteMenus` 傳入契約；`LegacyRemoteMenu` 按實際槽位資料建立介面、依 wire property ID 查表，不再把稀疏 ID 當陣列索引。新的資料路徑沒有寫死模組名稱或物品名稱。

## 尚未通用的部分

**完整 GUI 尚未通用。執行准入仍為 GRID9_FUEL_RESULT 的 47 格、2 個 property 家族。** GUI 建構、背景繪製、Shift-click 三個指紋仍保留，畫布／256×256 貼圖與 IGuiHandler 分流仍有限。抽取器能描述不同槽位數，不代表固定畫面已能承接它們；不合家族者拒絕。

新抽取器不涵蓋的寫法，只有仍通過全部原先五個指紋及來源聯結檢查才可走舊模板。schema 2 明確標記 `SOURCE_TOPOLOGY` 或 `LEGACY_TEMPLATE`，後者附排除原因，不偽裝為抽取成功。schema 1 舊輸出與舊建構式保留相容路徑。原始 Bamboo JAR 本回合不可用，尚未重跑它的 corpus，所以不能宣稱該模組已全部走新路徑。

## 實測範圍

- 44 組合成正反例、1,185 個核心斷言通過：37/38/45/47/55 格、不同座標／property 數量／稀疏與改序 ID、迴圈/helper、JarReader、錯誤與預算退出。
- 635 個消費接線斷言通過：實際 encoder/parser/menu/dispatcher 方法，搭配明確 recording doubles。外層 GUI/registry family Proof 由測試提供，沒有宣稱完整來源准入成功。
- 3 份實際 encoder JSON 由 Python 獨立讀回，保留不同座標與 property ID 6/87。
- 13 個原始碼套用器測試通過：基準 hash、保留 rev200、preflight、重複套用、symlink、防覆寫及 I/O 失敗回復。
- rev200 本回合重跑 862 個核心斷言、144 個 adapter 斷言與 10 個套用測試，全部通過。預設 observe 不變；翅膀偶發上托仍未經遊戲確認解決。

JDK 21.0.11，Java 測試使用 `-Xverify:all`。核心用本地 Kotlin 隨附 ASM9（只重定位 shaded 套件名）及裁剪版真實 Gson；MC/Fabric/registry/resource/GUI host 介接用測試替身。磁碟 JsonParser 未執行。沒有 Maven 原樣完整依賴建置、原始 Bamboo 實測、真實渲染／點擊／拖曳／交易封包、Mixin 或連服驗收。測試資料不是使用者擷取的跳躍封包。

## 還原／校驗

延續既有 source-checkpoint 儲存方式。五個 base64 文字片段是單一 XZ 壓縮來源 JSON，包含完整修改後 Java、獨立測試、套用器與日誌，不含原始模組、遊戲 class 或依賴 JAR。`unpack.py` 驗證固定 payload SHA-256 與每檔 SHA-256，且只寫新目錄。`source-index.json` 列出解包路徑／大小／hash。

```sh
# 只展開此次 delta：
python checkpoints/rev201/unpack.py --output ../LegacyForgeBridge-rev201-delta
# 用完整 GitHub checkout 還原累積來源：
python checkpoints/rev201/restore.py --output ../LegacyForgeBridge-rev201-source
```

已還原 rev200 時，也可對解包後 delta 執行：

```sh
python apply.py --source-root /path/restored-rev200 --check-only
python apply.py --source-root /path/restored-rev200
python tests/run_validation.py --classpath '/path/asm.jar:/path/asm-tree.jar:/path/asm-analysis.jar:/path/gson.jar' --work /path/new-validation
python tests/test_apply.py
```

Windows classpath 用分號。apply 驗證五個待改 GUI 檔案、rev200 三個來源檔、跳躍接線與舊 fingerprint，遇差異停止；不拿舊檔覆蓋未知後续改動。GUI 基準由 rev194 前三個 Git blob 恢復完整 source.patch，SHA-256 與其 restore.py pin 相同；不是完整累積還原鏈已實跑。此次只在 reviewed-file fixtures 測試 apply；完整 restore 尚未實跑。

還原後 converter fingerprint 為 `2026-09-26.201-source-menu-topology`，供未來真正建置版本重新轉換舊快取；不是已發行 JAR 版本聲明。GitHub 根 src 仍不是最新累積來源，不能直接編譯它冒充 rev201。没有呼叫 Actions。
