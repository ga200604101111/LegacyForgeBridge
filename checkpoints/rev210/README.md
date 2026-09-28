# rev210 — SOURCE ONLY：數值 helper 回傳與來源方法派發

接續 `76939f9f2ac51b972a487e611eccfb43718d4b18`（2026-09-27 rev209）。
僅用於 `feature/generic-conversion-iyamato-corpus3`。

**本次是已編譯、已執行隔離測試的兩份產品來源碼修正，不是可安裝更新。沒有建立完整主 JAR，沒有跑 Minecraft 或連原服。不要把這份 ZIP 放進 mods，也不要取代 rev209 主檔。**

## 實際修正

1. `ClientFeedbackCompiler` 與 `ClientFeedbackProgram` 支援 `FRETURN`／`DRETURN`。rev209 已能運算 float/double，也能呼叫 helper，卻在 helper 浮點回傳時產生 `DEFER`，使後續來源揮手條件無法繼續。現在沿原呼叫鏈返回數值；檢查回傳型別與堆疊，未知值仍中止於 DEFER，不猜數值。
2. 來源內可解析的方法本文，優先於 `DURATION`／`MAX` 等輸入純量捷徑。rev209 對來源可見的 `super.func_77626_a`／`super.func_77612_l` 可能用子類目前數值代替父類方法結果；現在保留 invokespecial 的父類解析與 invokevirtual 的實際來源覆寫。來源覆寫內有未知世界操作時不再被純量捷徑繞過。

沒有以武器名稱、模組名稱或觀測到的數字 ID 分流。沒有新增傷害、爆炸、耗彈、耐久或左鍵攻擊的客端重播。没有更動 runtime callback、GUI、跳躍、投射物或 renderer 的來源。本次沒有主 JAR，故不宣稱已驗證那些路徑的完整產品回歸。

## 本次真正執行的驗證

使用 JDK 21.0.11，產品來源 `javac --release 21`；合成來源 `javac --release 8`。
真實 ASM/Gson class bytes 由既有 ViaVersion/ViaProxy artifact **10832672769** 擷取，逐 class 與下載來源比對相同。不是 Maven clean resolve，沒有建立新 workflow。詳細 hash 見 `validation/dependency-provenance.json`。

- 新增 **21,902 項合成來源斷言：候選 0 失敗**。同一批測試對精確 rev209 兩份來源有 **14,536 項失敗**，包含浮點回傳、來源可見父類呼叫及未知來源覆寫。
- 來源由 javac 產生真正 class，分別交由 JVM 與產品的 Compiler/VM 執行，比較揮手次數；不是手填預期值。Minecraft 類別是測試専用記錄 host，不是真實遊戲。
- 覆蓋 float/double、static/private/virtual/super helper、double 寬參數槽位、release/entity、數值除數 20→40 變異、全部來源類別重新命名、NaN／Infinity／負零、錯型別／多餘回傳堆疊／未知回傳值。172 次合成來源方法 BasicVerifier 檢查。
- 原封不動重跑 rev209 `FeedbackSafetyTest`，基底與候選均 **10 項通過**：未知事件、伺服器資源變更後的未知值、覆寫派發、VM 上限。
- 本次兩個產品型別及內部類別，共 **11 class／73 methods**，ASM BasicVerifier 通過。這不是整個橋接器的 bytecode audit。
- 兩個獨立輸出目錄的產品 javac 結果，全部 class 位元組一致。不是完整主 JAR 可重現建構。
- 驗證用來源与依賴輸入 hash 執行前後不變。

`compile-only/ConversionContext` 僅為編譯宣告，每個方法都拋錯；沒有呼叫 `ClientFeedbackCompiler.apply()` 或整個 LegacyConversionEngine。`fixture-hosts` 是合成參考測試，與產品輸出分離。舊版完整 3,886 項回歸、原始 iYAMATO/Bamboo/RPGTool corpus、Fabric/Mixin/Via 與原服均未於本次重跑。斷言数量不等於支援武器／技能數量。

## 從 GitHub 解包

```sh
python checkpoints/rev210/unpack.py --output ../lfb-rev210-source-only
```

這會還原 35 份本次來源、基底參考與驗證紀錄；不是完整主檔建構 kit。

## 重跑

需 JDK 21 與真實 ASM core/tree/commons/analysis、Gson JAR；本 kit 不含依賴。

```sh
python verify.py --classpath "/path/asm-all-classes.jar:/path/gson.jar" --work ../rev210-verify-new
```

Windows classpath 分隔符改成 `;`。`--work` 必須是 kit 外的新目錄。

驗證器會先核對全部編譯／測試來源 hash，以及獨立釘住的 rev209 來源 hash；測基底必須重現預期失敗，再測候選必須全過。原版兩份來源保存在 `baseline/`，只用來證明差異，不是另一套待安装的產品。`source-changes.diff` 與 `changes.json` 提供精確變更與前後 hash。

## 累積來源與交付界線

本次已還原並驗證 rev209 六個 payload 分片、XZ SHA-256、全部 70 個 delta 項目及所用來源在 `source-sha256.json` 的 hash。**沒有還原 rev208 傳來的 199 份來源；這不是完整 rev209 的 269 檔 kit，也不是全倉庫 clean build。**

本 kit 的 `src/` 只有兩份取代來源。未來整合時要先還原完整 rev209 kit，核對 `changes.json` 所列被取代來源 hash，只替換這兩份檔案，保留其他累積來源。此處不是根目錄舊 `src` 的替代完整 checkout。

要製作完整下一版主 JAR，尚需精確 rev209 主檔：

`legacyforgebridge-0.2.0-alpha.27-rev209-local-test.1.jar`

SHA-256：`f9cce93d55b745a5de286351a70304ed051157caebd505b0dffdcb2607fb19d7`。

還需原始模組 corpus、完整累積 kit 與真實遊戲整合驗證。屆時必須另更新主檔版本／轉換指紋並處理轉換快取失效，再重跑完整回歸。**不得直接使用 rev209 舊 rebuild/integrator 把新來源包成同一 rev209 版本號；本次沒有提供或宣稱已驗證該整合。**

特殊彈體、完整裝填／連射客端 tick、其他特殊能力、任意 GUI 與實機驗收仍未完成。本次沒有修改原模組 JAR 或伺服器狀態，也沒有推 main/Bamboo、改 workflow、dispatch Actions、開 PR、tag 或 release。
