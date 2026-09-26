# rev199 — 通用靜態欄位貼圖來源證據（僅原始碼）

**尚未接入轉換流程，不是可安裝版本，也沒有新增可用的圖示／實體規則。**

接續 `feature/generic-conversion-iyamato-corpus3` 的 rev198：
`99380ad408445e932a259a27f3b300f985ee08cf`。`main` 的 rev197 不變。

## 這次實作

新增 `LegacyFluentTextureAnalyzer`。它只讀取 class 位元組碼，追蹤同一個 NEW allocation 經原版 MCP/SRG fluent setter 設定貼圖，再寫入確切的靜態欄位。它不執行來源模組、不建立 Minecraft 物件、不猜 Material，也不以模組名稱、武器名稱或註冊名稱猜貼圖。

結果保留欄位 owner/name/descriptor、來源實作類別、literal texture、PNG 路徑、檔案是否存在，以及來源方法與寫入指令位置。這是「該欄位寫入時的貼圖設定證據」，不是最終預設圖示或模型已可用的證明。

防誤判：排除多重欄位寫入、缺失／循環繼承、來源覆寫 setter、未知 fluent 方法、區域變數別名、跨分支配置、額外呼叫／欄位逸出、非字面貼圖參數、不配對的建構呼叫，以及不安全資源路徑。找不到 PNG 時保留明確的 `resourcePresent=false`，不宣稱資源可用。

## 實際輸入與結果

輸入：`iYAMATOs-Mod-1.7.10.jar`，1,029,340 bytes。
SHA-256：`35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`。

| 檢查項目 | 結果 |
|---|---:|
| 靜態欄位字面貼圖證據 | 101 |
| 獨立掃描的直接 registerItem 欄位 | 99 |
| 與上述註冊欄位精確配對 | 99 |
| 證據中缺少 PNG | 0 |
| 未直接註冊的額外欄位 | 2 |
| 不符合此分析器範圍的欄位 | 7 |
| 合成正／反例 assertions | 101 通過 |

兩筆額外欄位為 `ItemRegister.vanadiumOre` 與 `ItemRegister.trollScale`；沒有算成新增可用物品。7 個排除為 4 個 BlockRegister 方塊欄位與 3 個 ItemRegister 方塊欄位：它們沒有符合這條分析路徑的外部字面貼圖 setter。這不代表它們本身沒有貼圖。

`CorpusProbe` 的配對只證明 `GameRegistry.registerItem(Item,String)` 第一參數來自同一 GETSTATIC 欄位；它不取代既有註冊名稱分析，也未證明生命週期執行順序與設定分支。完整欄位結果可由 `verify.py` 重建，下載原始碼包另附 `validation/corpus.tsv`；測試環境與限制見 `verification.json`。

## 驗證範圍

此環境沒有可下載的外部 ASM/Gson 或完整建置相依套件。**實際通過的是 JDK 21.0.11 內附 ASM 的隔離測試副本**：僅把測試副本中的 `org.objectweb.asm` 改為 `jdk.internal.org.objectweb.asm`、ASM9 改 ASM8；產品原始碼不變。以 `-Xverify:all` 執行合成測試與 supplied-corpus 靜態掃描。

沒有完成產品 ASM9 原樣編譯、完整 Gradle/Loom 建置、Minecraft/Fabric/Mixin 啟動、伺服器實測、累積 rev189–199 還原鏈驗證或 Actions 建置。`restore.py` 只做語法與防覆寫／缺少前版檔案拒絕測試。不能把這些測試解讀為遊戲相容性驗證。

使用真正 ASM9 core/tree/analysis 相依套件可重跑：

```sh
python checkpoints/rev199/verify.py --asm-classpath '/path/asm.jar:/path/asm-tree.jar:/path/asm-analysis.jar' --corpus /path/iYAMATOs-Mod-1.7.10.jar --work /path/new-validation
```

Windows classpath 使用分號。重現本次受限測試：

```sh
python checkpoints/rev199/verify.py --jdk-internal-asm --corpus /path/iYAMATOs-Mod-1.7.10.jar --work /path/new-smoke-test
```

`--work` 必須是不存在的新目錄。驗證器會先檢查原始碼 SHA-256，測試失敗即以非零代碼結束，不安裝或執行來源模組。

## 還原與未完成項目

根目錄 `src` 仍是舊 rev188；不要直接編譯後當成新版。取得 rev198 加上這次新增工具：

```sh
python checkpoints/rev199/restore.py --output ../LegacyForgeBridge-rev199-source
```

必須在完整儲存庫執行，保留先前 checkpoint。還原器先呼叫 rev198 還原器，再新增此 Java 類別與獨立驗證工具；拒絕覆寫既有目錄。

下一段應在真正 rev198 還原來源上，把欄位證據和既有 registry binding 接合，補上生命週期／後續變異檢查，再通過 icon callback 與 resource/model 產出條件。不能直接拿字面 texture 塞進未建構物件，繞過建構／渲染驗證。方塊建構子內的貼圖與 54 個實體的 ID、狀態、模型、武器互動仍待處理。

本次**沒有**修改既有 engine、cache fingerprint、預設圖示分析、執行階段准入條件或 rev198 發行 JAR。因此先前 103 個模型未解、54 個實體未准入的結果未被本次工作改寫；沒有交付或冒充可玩的 converted iYAMATO JAR。
