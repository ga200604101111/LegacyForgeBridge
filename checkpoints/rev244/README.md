# rev244 — 診斷欄位與捕捉範圍修正（僅原始碼）

日期：2026-10-03（Asia/Taipei）。
分支：`feature/generic-conversion-iyamato-corpus3`。
接續基準：`e6c4a7879b9c9407761ed133df043e97b45daefe`。

**本 checkpoint 沒有產生新的可安裝主 JAR，也沒有宣稱修復遊戲內二次拉升。**

## 本次實際修改

1. `SOURCE_HANDLER_*` 把 `field_70133_I` 的錯誤標籤 `isAirBorne` 改為 `velocityChanged`。只改記錄名稱，沒有修改欄位的讀取、寫入、來源事件 dispatch 或速度套用。
2. 每次真正建立新 capture，重設 camera / move / tick-start 計數。原本這些是 JVM 累計值，前一段有觀測不代表新一段也有。已啟用時重複 start 不重設。
3. START_CLIENT_TICK 只計入有效捕捉、legacy multiplayer、主執行緒及相符本機玩家。補上連續 100 次有效 tick-end 仍沒有 tick-start 的警告；沒有 tick-end 時不宣稱能偵測其缺失。
4. 新 capture 重設診斷警告去重與目前執行緒的未配對 move 記錄，避免跨捕捉錯配；同一 capture 的真正缺失仍照常報告。這些 hook 依既有契約在 client thread 使用，不新增跨執行緒 capture 安全性保證。

`DIAGNOSTIC_CONFIG` 新增 `diagnosticSchema=rev244-source.1`、capture-local 計數範圍及來源欄位名稱。沒有重新命名既有類別或變更公開方法簽章。

## 可重現來源與測試

從 repository 根目錄執行：

```sh
python3 checkpoints/rev244/test_local.py
python3 checkpoints/rev244/prepare.py --output ../lfb-rev244-diagnostic-sources
```

需要本機 Python 3.10+ 與 JDK 21。沒有 pip/Maven/Gradle 依賴下載，也不會呼叫 Actions。

`prepare.py` 先核對兩份 rev243 來源的 SHA-256，再執行次數嚴格限定的文字修改，輸出完整的兩份候選 Java 原始碼及 manifest。拒絕未知來源、改過的來源、現有輸出目錄，以及 root src / checkpoints 內的輸出位置。歷史 rev243 來源保持原樣。`diagnostics.patch` 是供審查的相同差異，不應直接套回歷史 checkpoint。

本輪執行結果：同一套 20 個命名情境，rev243 有 12 個預期失敗、8 個通過；候選版全部通過，共 643 個 Java 檢查，另外 9 個來源準備防護檢查。包含停止／重開、模擬重連、未配對 move、缺失 hook、警告再揭露、單次 source dispatch、取消狀態、例外身分、不記錄無關聊天指令，以及 200 組不改動位置／速度／輸入／鏡頭的觀測案例。命名情境不是 20 種遊戲功能支援。

實際編譯的是兩份真實診斷 Java 來源及原版 `Rev242ClientAccess`，使用 `javac --release 21`，各情境以新 JVM 與 `java -Xverify:all` 執行。Minecraft/Fabric 類別使用既存 rev243 test doubles；本 checkpoint 明確提供 `LegacyBehaviorApi` 與記憶體 writer doubles。**沒有測試真實磁碟 writer、輪替、queue loss、Mixin、Minecraft、原模組 handler 或伺服器。** 本輪未重跑前次 1,048 項測試，也沒有新使用者實測日誌。

來源由 GitHub connector 讀取，輸入先核對 Git blob SHA 及 SHA-256。參考來源及測試雜湊見 `verification.json` / `source-manifest.json`。完整逐例結果由 `test_local.py` 寫至 `test-results.json`；它是本地測試證據，不是實機遊戲記錄。

## 主 JAR 與建構邊界

最新先前交付仍是：

`legacyforgebridge-0.2.0-alpha.27-rev243-corpus4-local.27-diagnostic.jar`

SHA-256：`87d4e3cbe4939b21c4eb955b66508bbc2724aafbf6b557c87245cc345121ef63`。

交接已明確記錄該完整二進位沒有上傳至 GitHub；本輪也沒有該附件。因此沒有執行主 JAR 的增量組裝、完整 class-entry 比對或遊戲啟動。不以 root src 重新編譯冒充累積版本，也不提供把 test doubles 包成輔助修補模組的假成品。

來源 delta 刻意保留 rev243 的 artifact 版本字串與類別 ABI，因為這不是一份已完成版號與封裝更新的主 JAR。後續取得上述 exact-base 時，還需統一 BuildInfo / fabric.mod.json / trace 版本與提示、維持既有 mixin 與依賴，再做完整主 JAR 建構及保護項目比對；不能僅重編這兩個類別就宣稱已交付 rev244。

## 跳躍調查的下一個證據缺口

先讀 [rev243 交接](../rev243/investigation/2026-10-03/HANDOFF.md)。仍沒有同服、同角色、同翅膀、同位置的原生 Forge 1.7.10 配對結果。現有 rev243 資料已足夠做先前分析，不要求重做相同 1.21.11 捕捉。119 筆本機速度封包與 114 次起跳的數量差，不能替代逐筆 server causal proof。

本次來源核對也讀取固定版本的參考 `EntityTrackerEntry.java`：`CodeMajorGeek/lwjgl3-mcp908@2a6d28f2b7541b760ebb8e7a6dc905465f935a64`，其中 `velocityChanged` 與 `isAirBorne` 確為兩個不同同步判斷。這仍不是使用者實際伺服器封包呼叫來源的證明。

不恢復 rev241/242 速度歷史 heuristic、不取消原始 source jump 加成、不改 rev240 溫泉或 rev242 落地粒子、不改原模組或伺服器。只在指定分支提交，包含 `[skip ci] [skip actions]`，不開 PR、不建 tag/release、不修改或 dispatch Actions。
