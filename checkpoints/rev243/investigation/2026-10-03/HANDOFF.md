# rev243 跳躍問題 — 暫停與接續紀錄

保存日期：2026-10-03（Asia/Taipei）。使用者要求先保存到 GitHub，下次再繼續。

**狀態：跳躍問題尚未修復。這筆是文件／分析保存，不是新修正版。**

## 下次先讀這裡

- 專案：`ga200604101111/LegacyForgeBridge`
- 唯一工作分支：`feature/generic-conversion-iyamato-corpus3`
- 目前程式基準提交：`6399f44aad1e695bb1cb49d444a3d13e772c2861`
- 最新交付主 JAR：`legacyforgebridge-0.2.0-alpha.27-rev243-corpus4-local.27-diagnostic.jar`
- JAR SHA-256：`87d4e3cbe4939b21c4eb955b66508bbc2724aafbf6b557c87245cc345121ef63`
- rev243 是唯讀移動診斷版；rev241／242 的歷史速度補償已硬停用。不得把它稱為跳躍完成版。
- 保留原始模組跳躍加成、rev240 溫泉修正及 rev242 翅膀落地粒子修正。
- 本次沒有變動 Java 原始碼、JAR、版本號、原始模組或伺服器；沒有重新建構或執行遊戲測試。

## 保存的文件

- [實測日誌分析](rev243-log-analysis.txt)：114 次起跳、119 個速度封包、五個使用者標記、鏡頭和粒子結果、未解問題。
- [機器可讀統計](rev243-log-analysis.json)：事件數、封包間隔、標記附近的封包、各類事件統計及限制。
- [出站 onGround 核對](rev243-outbound-ground-check.txt)：2,363 筆可對上位置的封包，以及第 25 跳的 11 筆出站紀錄。
- [來源及雜湊清單](manifest.json)：保存檔案與未上傳附件的 provenance。

以上三份分析文件保留原內容。其「本次沒有推送 GitHub／沒有 repository change」敘述指當時的分析步驟；此保存提交是在分析完成後新增的文件提交。

完整原始 `logs.zip`、完整摘錄 `rev243-log-evidence.txt`、交付主 JAR 與第三方模組 JAR 沒有納入這筆文件提交。來源檔名、雜湊和必要原始行號已保留；不要把附件存在過當成它們已上傳到 GitHub。rev243 程式、建構腳本與測試早已在 `checkpoints/rev243/`，根目錄 `src/` 不是最新累積交付狀態。

## 已確認，與尚未確認

使用者 rev243 捕捉時間為 2026-10-02 23:27:15.820 至 23:29:53.118（台灣）。末尾完整性統計為 67,095 筆、沒有 dropped／truncated／discarded。

本地 source jump 執行 114 次，每次只有一個 handler、沒有 same-tick repeat，垂直速度都是約 0.42 → 0.57。119 個本機速度封包均原樣套用 XYZ；本次全部正 Y、X/Z 為零。有 5 次在空中從負 Y 變正 Y，另 2 次在已落地時收到正 Y。這是玩家速度被更新，不只是鏡頭看起來上移。

20,403 筆 Camera.update 返回點的基本 Y 插值符合記錄；這不涵蓋最終視角搖晃、受傷鏡頭或光影矩陣。已量測的 raw-hook → Via／apply 時間不支持在那段流程卡住數百毫秒，但不是 RTT，也不能看見伺服器發送前的時間。

出站核對：2,363 筆可對上位置的封包沒有 onGround 差異；580 筆 idle 沒有座標，另 1 筆尚未匹配。第 25 跳第二次拉升前 11 筆出站紀錄均 onGround=false。不能直接歸因為客戶端半空誤報落地；也不能因此推定伺服器內部碰撞／輸入狀態正確。

翅膀：122 次來源落地事件，每次入列 10 個 flame，共 1,220 個。這是事件／入列證據，不是 GPU 可見性驗證。

仍未知：每筆伺服器速度的實際呼叫來源、部分跳躍為何有第二筆速度、同樣條件下原生 Forge 1.7.10 是否也如此。localJump 是客戶端觀察標籤，不是伺服器因果 ID。119 筆封包與 114 次起跳的數量差，不足以逐筆證明重複同步。

## 重要更正：velocityChanged 不是 isAirBorne

原 RPGTool 起跳邏輯：`motionY += 0.15D; field_70133_I = true;`。

`field_70133_I` 的 1.7.10 MCP 名稱是 **velocityChanged**。之前部分說明，以及 rev243 SOURCE_HANDLER 日誌中的 isAirBorne 標籤，解讀／命名錯誤。本紀錄更正該解讀，**尚未修改交付 JAR 的標籤**。不能宣稱這個日誌名稱錯誤本身造成多發封包。

既有研究核對的 1.7.10 參考 EntityTrackerEntry 會因 velocityChanged 建立 S12PacketEntityVelocity 並發給追蹤者和玩家本人。這是有來源支持的可能同步路徑，不是使用者實際伺服器所有封包的因果證明。

參考來源固定版本：`CodeMajorGeek/lwjgl3-mcp908@2a6d28f2b7541b760ebb8e7a6dc905465f935a64`；`conf/version.cfg`、`conf/fields.csv`、`src/minecraft_server/net/minecraft/entity/EntityTrackerEntry.java`。這是先前研究結果的保存，不是本次新研究。

## 下次接續點

1. 接續前先讀 `AGENTS.md`、本文件與遠端最新分支，避免覆蓋後續工作。
2. 尚未收到原生 Forge 1.7.10 的對照結果。上一個建議是同服、同角色、同翅膀、同位置連跳 10～20 次，先確認原生端是否也有二次拉升。使用者本回合要求暫停，不能把待測項寫成已完成。已有 rev243 對照資料，不要求重跑完全相同的 1.21.11 捕捉。
3. 原生端正常：追第一個新舊客戶端行為差異，檢查來源事件生命週期、輸入、出站移動順序、碰撞／落地、速度套用時序；以有證據的通用相容修正處理。
4. 原生端也異常：先查來源模組／伺服器同步路徑，伺服器追蹤或修正需另外授權；目前保持伺服器和原模組不變。
5. 下次有實際診斷程式修改時，更正 SOURCE_HANDLER 欄位標籤，保留 read-only 及檔案完整性指標。

## 不要重走的方向

不要恢復「只因 Y 符合歷史就抵消」作為通用修復，不要硬加固定跳高或高度上限，不要把所有向上／向下速度擋掉。rev241／242 的 heuristic 可能干預合法技能施力，使用者已不接受這種手感與風險。

驗收必須區分：使用者實機記錄、本地 mock 回歸、原始碼路徑比對、推論。不要把 1,048 項診斷程式測試說成跳躍相容已驗證；目前未有原生 1.7.10 配對實測。

後續仍只使用本地建構，交付完整主 JAR；提交包含 `[skip ci] [skip actions]`；不開 PR、不建立 tag/release、不 dispatch 或修改 Actions，不 force push，不改 main 或 Bamboo 分支。
